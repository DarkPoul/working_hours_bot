package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.bot.BotResponse;
import esvar.ua.workinghoursbot.bot.KeyboardFactory;
import esvar.ua.workinghoursbot.domain.Location;
import esvar.ua.workinghoursbot.domain.RegistrationSession;
import esvar.ua.workinghoursbot.domain.RegistrationState;
import esvar.ua.workinghoursbot.domain.RegistrationStatus;
import esvar.ua.workinghoursbot.domain.Role;
import esvar.ua.workinghoursbot.domain.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private static final Pattern LAST_NAME_PATTERN = Pattern.compile("^[\\p{L}\\p{M}][\\p{L}\\p{M} '\\-]{1,63}$");
    private static final int LOCATION_PAGE_SIZE = 8;
    private static final String BUTTON_BACK = "⬅️ Назад";
    private static final String BUTTON_RESTART = "🔁 Почати спочатку";
    private static final String BUTTON_REFRESH = "🔄 Оновити";
    private static final String BUTTON_NEXT = "➡️ Наступні";
    private static final String BUTTON_PREV = "⬅️ Попередні";
    private static final String LOCATION_SEPARATOR = " | ";

    private final UserAccountService userAccountService;
    private final RegistrationSessionService registrationSessionService;
    private final LocationService locationService;

    public BotResponse startRegistration(Long telegramUserId, Long chatId) {
        Optional<UserAccount> existingAccount = userAccountService.findByTelegramUserId(telegramUserId);
        if (existingAccount.isPresent()) {
            UserAccount account = existingAccount.get();
            return handleExistingAccount(chatId, account);
        }

        RegistrationSession session = registrationSessionService.findByTelegramUserId(telegramUserId)
                .orElseGet(RegistrationSession::new);
        session.setTelegramUserId(telegramUserId);
        session.setState(RegistrationState.ENTER_FULL_NAME);
        session.setDraftLastName(null);
        session.setDraftRole(null);
        session.setDraftLocationPage(0);
        registrationSessionService.save(session);

        log.info("Start registration for telegramUserId={}", telegramUserId);
        SendMessage message = buildMessage(chatId, "Вітаю! Введіть, будь ласка, ваше прізвище та ім'я.");
        message.setReplyMarkup(KeyboardFactory.enterNameKeyboard());
        return BotResponse.of(message);
    }

    public BotResponse cancelRegistration(Long telegramUserId, Long chatId) {
        registrationSessionService.deleteByTelegramUserId(telegramUserId);
        log.info("Registration cancelled for telegramUserId={}", telegramUserId);
        return BotResponse.of(buildMessage(chatId, "Реєстрацію скасовано."));
    }

    public BotResponse handleText(Long telegramUserId, Long chatId, String text) {
        if (isStartCommand(text) || BUTTON_RESTART.equalsIgnoreCase(text)) {
            return restartRegistration(telegramUserId, chatId);
        }

        Optional<UserAccount> accountOptional = userAccountService.findByTelegramUserId(telegramUserId);
        if (accountOptional.isPresent()) {
            return handleAccountText(chatId, text, accountOptional.get());
        }

        Optional<RegistrationSession> sessionOptional = registrationSessionService.findByTelegramUserId(telegramUserId);
        if (sessionOptional.isEmpty()) {
            return BotResponse.of(buildMessage(chatId, "Немає активної реєстрації. Надішліть /start."));
        }

        RegistrationSession session = sessionOptional.get();
        if (session.getState() == RegistrationState.ENTER_FULL_NAME) {
            String normalized = normalizeLastName(text);
            if (normalized == null) {
                SendMessage errorMessage = buildMessage(chatId, "Прізвище має містити лише літери та бути довжиною 2-64 символи.");
                errorMessage.setReplyMarkup(KeyboardFactory.enterNameKeyboard());
                return BotResponse.of(errorMessage);
            }

            session.setDraftLastName(normalized);
            session.setState(RegistrationState.CHOOSE_ROLE);
            registrationSessionService.save(session);
            log.info("Registration state transition telegramUserId={} -> CHOOSE_ROLE", telegramUserId);

            SendMessage message = buildMessage(chatId, "Оберіть вашу роль:");
            message.setReplyMarkup(KeyboardFactory.roleMenuKeyboard());
            return BotResponse.of(message);
        }

        if (session.getState() == RegistrationState.CHOOSE_ROLE) {
            return handleRoleSelection(session, chatId, text);
        }

        if (session.getState() == RegistrationState.CHOOSE_LOCATION) {
            return handleLocationSelection(session, chatId, text);
        }

        SendMessage message = buildMessage(chatId, "Будь ласка, використовуйте кнопки для вибору.");
        return BotResponse.of(message);
    }

    private BotResponse handleRoleSelection(RegistrationSession session, Long chatId, String text) {
        if (BUTTON_BACK.equalsIgnoreCase(text)) {
            session.setState(RegistrationState.ENTER_FULL_NAME);
            session.setDraftLastName(null);
            session.setDraftRole(null);
            registrationSessionService.save(session);
            SendMessage message = buildMessage(chatId, "Введіть, будь ласка, ваше прізвище (2-64 символи).");
            message.setReplyMarkup(KeyboardFactory.enterNameKeyboard());
            return BotResponse.of(message);
        }

        Role role = resolveRole(text);
        if (role == null) {
            SendMessage response = buildMessage(chatId, "Будь ласка, оберіть роль кнопками.");
            response.setReplyMarkup(KeyboardFactory.roleMenuKeyboard());
            return BotResponse.of(response);
        }

        session.setDraftRole(role);
        session.setState(RegistrationState.CHOOSE_LOCATION);
        session.setDraftLocationPage(0);
        registrationSessionService.save(session);
        log.info("Registration state transition telegramUserId={} -> CHOOSE_LOCATION", session.getTelegramUserId());

        return showLocationsPage(session, chatId, 0);
    }

    private BotResponse handleLocationSelection(RegistrationSession session, Long chatId, String text) {
        if (BUTTON_BACK.equalsIgnoreCase(text)) {
            session.setState(RegistrationState.CHOOSE_ROLE);
            session.setDraftRole(null);
            registrationSessionService.save(session);
            SendMessage message = buildMessage(chatId, "Оберіть вашу роль:");
            message.setReplyMarkup(KeyboardFactory.roleMenuKeyboard());
            return BotResponse.of(message);
        }

        int currentPage = Optional.ofNullable(session.getDraftLocationPage()).orElse(0);
        if (BUTTON_NEXT.equalsIgnoreCase(text) || BUTTON_PREV.equalsIgnoreCase(text)) {
            int nextPage = currentPage;
            if (BUTTON_NEXT.equalsIgnoreCase(text)) {
                nextPage = currentPage + 1;
            } else if (BUTTON_PREV.equalsIgnoreCase(text) && currentPage > 0) {
                nextPage = currentPage - 1;
            }
            session.setDraftLocationPage(nextPage);
            registrationSessionService.save(session);
            return showLocationsPage(session, chatId, nextPage);
        }

        String locationCode = parseLocationCode(text);
        if (locationCode == null) {
            SendMessage response = buildMessage(chatId, "Будь ласка, оберіть локацію кнопками.");
            response.setReplyMarkup(buildLocationKeyboard(currentPage));
            return BotResponse.of(response);
        }

        Optional<Location> locationOptional = locationService.findActiveByCode(locationCode);
        if (locationOptional.isEmpty()) {
            SendMessage response = buildMessage(chatId, "Локація не знайдена. Спробуйте ще раз.");
            response.setReplyMarkup(buildLocationKeyboard(currentPage));
            return BotResponse.of(response);
        }

        Location location = locationOptional.get();
        UserAccount account = createUserAccount(session, chatId, location);
        registrationSessionService.deleteByTelegramUserId(session.getTelegramUserId());
        log.info("Saved user account telegramUserId={} role={} status={} location={}", account.getTelegramUserId(),
                account.getRole(), account.getStatus(), location.getCode());
        SendMessage response = buildMessage(chatId,
                "Дякую! Заявку на реєстрацію створено. Очікуйте підтвердження.");
        response.setReplyMarkup(KeyboardFactory.pendingMenuKeyboard());
        return BotResponse.of(response);
    }

    private UserAccount createUserAccount(RegistrationSession session, Long chatId, Location location) {
        UserAccount account = new UserAccount();
        account.setTelegramUserId(session.getTelegramUserId());
        account.setTelegramChatId(chatId);
        account.setLastName(session.getDraftLastName());
        account.setRole(session.getDraftRole());
        account.setLocation(location);
        account.setStatus(RegistrationStatus.PENDING_APPROVAL);
        return userAccountService.save(account);
    }

    private static String normalizeLastName(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim().replaceAll("\\s+", " ");
        if (trimmed.length() < 2 || trimmed.length() > 64) {
            return null;
        }
        if (!LAST_NAME_PATTERN.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed;
    }

    private static Role resolveRole(String roleText) {
        if ("Продавець".equalsIgnoreCase(roleText)) {
            return Role.SELLER;
        }
        if ("Старший продавець".equalsIgnoreCase(roleText)) {
            return Role.SENIOR_SELLER;
        }
        if ("ТМ".equalsIgnoreCase(roleText)) {
            return Role.TM;
        }
        return null;
    }

    private static SendMessage buildMessage(Long chatId, String text) {
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .build();
    }

    private static String formatRole(Role role) {
        return switch (role) {
            case SELLER -> "Продавець";
            case SENIOR_SELLER -> "Старший продавець";
            case TM -> "ТМ";
        };
    }

    private static String formatStatus(RegistrationStatus status) {
        return switch (status) {
            case NEW -> "NEW";
            case PENDING_APPROVAL -> "PENDING_APPROVAL";
            case APPROVED -> "APPROVED";
            case REJECTED -> "REJECTED";
            case BLOCKED -> "BLOCKED";
        };
    }

    private BotResponse handleAccountText(Long chatId, String text, UserAccount account) {
        if (account.getStatus() == RegistrationStatus.PENDING_APPROVAL) {
            if (BUTTON_REFRESH.equalsIgnoreCase(text)) {
                return handleExistingAccount(chatId, account);
            }
            SendMessage response = buildMessage(chatId, "Ваша заявка ще на перевірці. Натисніть «🔄 Оновити».");
            response.setReplyMarkup(KeyboardFactory.pendingMenuKeyboard());
            return BotResponse.of(response);
        }

        if (account.getStatus() == RegistrationStatus.APPROVED) {
            SendMessage response = buildMessage(chatId,
                    "Ви вже зареєстровані. Роль: %s".formatted(formatRole(account.getRole())));
            response.setReplyMarkup(resolveMainMenuKeyboard(account));
            return BotResponse.of(response);
        }

        if (account.getStatus() == RegistrationStatus.REJECTED) {
            SendMessage response = buildMessage(chatId, "На жаль, вашу заявку відхилено. Можете почати спочатку.");
            response.setReplyMarkup(KeyboardFactory.pendingMenuKeyboard());
            return BotResponse.of(response);
        }

        SendMessage response = buildMessage(chatId, "Статус вашої заявки: %s".formatted(formatStatus(account.getStatus())));
        response.setReplyMarkup(KeyboardFactory.pendingMenuKeyboard());
        return BotResponse.of(response);
    }

    private BotResponse handleExistingAccount(Long chatId, UserAccount account) {
        if (account.getStatus() == RegistrationStatus.PENDING_APPROVAL) {
            SendMessage response = buildMessage(chatId, "Ваша заявка на перевірці. Натисніть «🔄 Оновити».");
            response.setReplyMarkup(KeyboardFactory.pendingMenuKeyboard());
            return BotResponse.of(response);
        }
        if (account.getStatus() == RegistrationStatus.APPROVED) {
            String text = """
                            ━━━━━━━━━━━━━━━━━━
                            👋 Вітаємо, %s
                            ━━━━━━━━━━━━━━━━━━
                            
                            👤 **Ваша роль**
                            %s
                            
                            📌 **Важливо**
                            Тут зʼявлятимуться важливі повідомлення
                            та службові сповіщення.
                            """.formatted(
                    account.getLastName(),
                    formatRole(account.getRole())
            );

            SendMessage response = buildMessage(chatId, text);
            response.setReplyMarkup(resolveMainMenuKeyboard(account));
            return BotResponse.of(response);
        }
        if (account.getStatus() == RegistrationStatus.REJECTED) {
            String text = """
                            ━━━━━━━━━━━━━━━━━━
                            👋 Вітаємо, %s
                            ━━━━━━━━━━━━━━━━━━
                            
                            👤 **Ваша роль**
                            %s
                            
                            📌 **Важливо**
                            Тут зʼявлятимуться важливі повідомлення
                            та службові сповіщення.
                            """.formatted(
                    account.getLastName(),
                    formatRole(account.getRole())
            );

            SendMessage response = buildMessage(chatId, text);
            response.setReplyMarkup(KeyboardFactory.pendingMenuKeyboard());
            return BotResponse.of(response);
        }
        SendMessage response = buildMessage(chatId,
                "Ви вже зареєстровані. Статус: %s Роль: %s".formatted(
                        formatStatus(account.getStatus()),
                        formatRole(account.getRole())));
        response.setReplyMarkup(resolveMainMenuKeyboard(account));
        return BotResponse.of(response);
    }

    private ReplyKeyboardMarkup resolveMainMenuKeyboard(UserAccount account) {
        if (account.getRole() == Role.TM) {
            return KeyboardFactory.tmMainMenuKeyboard();
        }
        return KeyboardFactory.mainMenuKeyboard();
    }

    private BotResponse restartRegistration(Long telegramUserId, Long chatId) {
        registrationSessionService.deleteByTelegramUserId(telegramUserId);
        Optional<UserAccount> existing = userAccountService.findByTelegramUserId(telegramUserId);
        existing.ifPresent(account -> {
            if (account.getStatus() != RegistrationStatus.APPROVED) {
                userAccountService.deleteByTelegramUserId(telegramUserId);
            }
        });
        return startRegistration(telegramUserId, chatId);
    }

    private boolean isStartCommand(String text) {
        return "/start".equalsIgnoreCase(text) || "🏠 /start".equalsIgnoreCase(text);
    }

    private BotResponse showLocationsPage(RegistrationSession session, Long chatId, int page) {
        SendMessage message = buildMessage(chatId, "Оберіть локацію:");
        message.setReplyMarkup(buildLocationKeyboard(page));
        return BotResponse.of(message);
    }

    private ReplyKeyboardMarkup buildLocationKeyboard(int page) {
        var locationsPage = locationService.findActivePage(page, LOCATION_PAGE_SIZE);
        List<String> buttons = locationsPage.stream()
                .map(location -> "📍 %s%s%s".formatted(location.getName(), LOCATION_SEPARATOR, location.getCode()))
                .toList();
        return KeyboardFactory.locationMenuKeyboard(buttons, locationsPage.hasPrevious(), locationsPage.hasNext());
    }

    private static String parseLocationCode(String text) {
        if (text == null || !text.contains(LOCATION_SEPARATOR)) {
            return null;
        }
        int index = text.lastIndexOf(LOCATION_SEPARATOR);
        if (index < 0 || index + LOCATION_SEPARATOR.length() >= text.length()) {
            return null;
        }
        return text.substring(index + LOCATION_SEPARATOR.length()).trim();
    }
}
