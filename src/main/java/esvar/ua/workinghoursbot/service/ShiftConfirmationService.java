package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.bot.BotResponse;
import esvar.ua.workinghoursbot.bot.KeyboardFactory;
import esvar.ua.workinghoursbot.domain.AuditEventType;
import esvar.ua.workinghoursbot.domain.Location;
import esvar.ua.workinghoursbot.domain.RegistrationStatus;
import esvar.ua.workinghoursbot.domain.ScheduleDay;
import esvar.ua.workinghoursbot.domain.ScheduleStatus;
import esvar.ua.workinghoursbot.domain.ShiftConfirmation;
import esvar.ua.workinghoursbot.domain.ShiftConfirmationStatus;
import esvar.ua.workinghoursbot.domain.SubstitutionRequest;
import esvar.ua.workinghoursbot.domain.UserAccount;
import esvar.ua.workinghoursbot.repository.ScheduleDayRepository;
import esvar.ua.workinghoursbot.repository.ShiftConfirmationRepository;
import esvar.ua.workinghoursbot.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShiftConfirmationService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final ZoneId KIEV_ZONE = ZoneId.of("Europe/Kiev");
    private static final String BTN_YES = "Так";
    private static final String BTN_NO = "Ні";

    private final ShiftConfirmationRepository confirmationRepository;
    private final ScheduleDayRepository scheduleDayRepository;
    private final UserAccountRepository userAccountRepository;
    private final SubstitutionService substitutionService;
    private final SubstitutionNotificationService substitutionNotificationService;
    private final TelegramSender telegramSender;
    private final AuditService auditService;
    private final MainMenuService mainMenuService;
    private final Clock clock;

    @Scheduled(cron = "0 0 12 * * *", zone = "Europe/Kiev")
    @Transactional
    public void sendDailyConfirmations() {
        LocalDate targetDate = LocalDate.now(clock.withZone(KIEV_ZONE)).plusDays(1);
        List<ScheduleDay> scheduleDays = scheduleDayRepository.findByDateAndStatus(targetDate, ScheduleStatus.WORK);
        if (scheduleDays.isEmpty()) {
            return;
        }
        Set<String> processed = new HashSet<>();
        for (ScheduleDay scheduleDay : scheduleDays) {
            Long telegramUserId = scheduleDay.getTelegramUserId();
            UUID locationId = scheduleDay.getLocationId();
            if (telegramUserId == null || locationId == null) {
                continue;
            }
            String key = telegramUserId + ":" + locationId;
            if (!processed.add(key)) {
                continue;
            }
            if (confirmationRepository.findByTelegramUserIdAndLocationIdAndDate(telegramUserId, locationId, targetDate)
                    .isPresent()) {
                continue;
            }
            Optional<UserAccount> accountOptional = userAccountRepository.findByTelegramUserId(telegramUserId);
            if (accountOptional.isEmpty()) {
                continue;
            }
            UserAccount account = accountOptional.get();
            if (account.getStatus() != RegistrationStatus.APPROVED || account.getLocation() == null) {
                continue;
            }
            ShiftConfirmation confirmation = new ShiftConfirmation();
            confirmation.setTelegramUserId(telegramUserId);
            confirmation.setLocationId(locationId);
            confirmation.setDate(targetDate);
            confirmation.setStatus(ShiftConfirmationStatus.PENDING);
            confirmation.setAskedAt(Instant.now(clock));
            confirmation.setAutoRejected(false);
            confirmationRepository.save(confirmation);

            SendMessage message = SendMessage.builder()
                    .chatId(account.getTelegramChatId().toString())
                    .text(buildQuestionText(account.getLocation(), targetDate))
                    .replyMarkup(KeyboardFactory.yesNoKeyboard())
                    .build();
            telegramSender.send(message);

            auditService.log(
                    AuditEventType.SHIFT_CONFIRM_REQUEST_SENT,
                    account.getId(),
                    null,
                    locationId,
                    "%s %s | Локація: %s".formatted(
                            DATE_FORMAT.format(targetDate),
                            account.getLastName(),
                            account.getLocation().getName()
                    )
            );
        }
    }

    @Scheduled(cron = "0 0 18 * * *", zone = "Europe/Kiev")
    @Transactional
    public void autoRejectPending() {
        List<ShiftConfirmation> pending = confirmationRepository.findByStatus(ShiftConfirmationStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }
        for (ShiftConfirmation confirmation : pending) {
            confirmation.setStatus(ShiftConfirmationStatus.NO);
            confirmation.setAutoRejected(true);
            confirmation.setRespondedAt(Instant.now(clock));
            confirmationRepository.save(confirmation);
            handleNegativeConfirmation(confirmation, true);
        }
    }

    @Transactional
    public BotResponse handleResponse(Long telegramUserId, Long chatId, String text) {
        if (!BTN_YES.equalsIgnoreCase(text) && !BTN_NO.equalsIgnoreCase(text)) {
            return BotResponse.empty();
        }
        Optional<ShiftConfirmation> confirmationOptional = confirmationRepository
                .findFirstByTelegramUserIdAndStatusOrderByAskedAtDesc(telegramUserId, ShiftConfirmationStatus.PENDING);
        if (confirmationOptional.isEmpty()) {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Немає активного запиту підтвердження.")
                    .replyMarkup(mainMenuService.mainMenuKeyboard(telegramUserId))
                    .build();
            return BotResponse.of(message);
        }
        ShiftConfirmation confirmation = confirmationOptional.get();
        if (BTN_YES.equalsIgnoreCase(text)) {
            confirmation.setStatus(ShiftConfirmationStatus.YES);
            confirmation.setRespondedAt(Instant.now(clock));
            confirmation.setAutoRejected(false);
            confirmationRepository.save(confirmation);
            handlePositiveConfirmation(confirmation);
            SendMessage message = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Дякуємо за підтвердження ✅")
                    .replyMarkup(mainMenuService.mainMenuKeyboard(telegramUserId))
                    .build();
            return BotResponse.of(message);
        }

        confirmation.setStatus(ShiftConfirmationStatus.NO);
        confirmation.setRespondedAt(Instant.now(clock));
        confirmation.setAutoRejected(false);
        confirmationRepository.save(confirmation);
        handleNegativeConfirmation(confirmation, false);
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text("Прийнято. Запускаємо пошук підміни.")
                .replyMarkup(mainMenuService.mainMenuKeyboard(telegramUserId))
                .build();
        return BotResponse.of(message);
    }

    private void handlePositiveConfirmation(ShiftConfirmation confirmation) {
        userAccountRepository.findByTelegramUserId(confirmation.getTelegramUserId())
                .ifPresent(account -> auditService.log(
                        AuditEventType.SHIFT_CONFIRM_YES,
                        account.getId(),
                        null,
                        confirmation.getLocationId(),
                        "%s %s | Локація: %s".formatted(
                                DATE_FORMAT.format(confirmation.getDate()),
                                account.getLastName(),
                                account.getLocation() != null ? account.getLocation().getName() : "-"
                        )
                ));
    }

    private void handleNegativeConfirmation(ShiftConfirmation confirmation, boolean autoRejected) {
        userAccountRepository.findByTelegramUserId(confirmation.getTelegramUserId())
                .ifPresent(account -> {
                    Location location = account.getLocation();
                    String locationName = location == null ? "-" : location.getName();
                    auditService.log(
                            autoRejected ? AuditEventType.SHIFT_CONFIRM_AUTO_NO : AuditEventType.SHIFT_CONFIRM_NO,
                            account.getId(),
                            null,
                            confirmation.getLocationId(),
                            "%s %s | Локація: %s".formatted(
                                    DATE_FORMAT.format(confirmation.getDate()),
                                    account.getLastName(),
                                    locationName
                            )
                    );

                    notifySenior(account, confirmation);
                    createSubstitutionRequest(account, confirmation, autoRejected);
                });
    }

    private void notifySenior(UserAccount account, ShiftConfirmation confirmation) {
        Location location = account.getLocation();
        if (location == null) {
            return;
        }
        substitutionService.findSeniorForRequest(location)
                .ifPresent(senior -> {
                    String text = """
                            ❗️Відмова від виходу на зміну
                            Працівник: %s
                            Дата: %s
                            Локація: %s
                            """.formatted(
                            account.getLastName(),
                            DATE_FORMAT.format(confirmation.getDate()),
                            location.getName()
                    );
                    SendMessage message = SendMessage.builder()
                            .chatId(senior.getTelegramChatId().toString())
                            .text(text.trim())
                            .build();
                    telegramSender.send(message);
                });
    }

    private void createSubstitutionRequest(UserAccount account, ShiftConfirmation confirmation, boolean autoRejected) {
        try {
            SubstitutionRequest request = substitutionService.createRequest(
                    account.getTelegramUserId(),
                    confirmation.getDate(),
                    false,
                    UUID.randomUUID(),
                    autoRejected ? "не підтвердив" : "не можу"
            );
            substitutionNotificationService.notifySeniorAboutRequest(request)
                    .forEach(telegramSender::send);
        } catch (Exception ex) {
            log.warn("Не вдалося створити запит на підміну після підтвердження. userId={}, date={}",
                    account.getTelegramUserId(),
                    confirmation.getDate(),
                    ex);
        }
    }

    private String buildQuestionText(Location location, LocalDate date) {
        String locationName = location == null ? "-" : location.getName();
        return """
                ✅ Підтвердження виходу на зміну
                Дата: %s
                Локація: %s
                Підтверджуєте?
                """.formatted(DATE_FORMAT.format(date), locationName).trim();
    }
}
