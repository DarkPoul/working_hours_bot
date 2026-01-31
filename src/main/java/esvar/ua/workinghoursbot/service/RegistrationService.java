package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.bot.BotResponse;
import esvar.ua.workinghoursbot.bot.CallbackData;
import esvar.ua.workinghoursbot.bot.KeyboardFactory;
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
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private static final Pattern LAST_NAME_PATTERN = Pattern.compile("^[\\p{L}\\p{M}][\\p{L}\\p{M} '\\-]{1,63}$");
    private static final List<String> LOCATIONS = List.of("Малишка 2Д", "Дарниця", "Позняки");

    private final UserAccountService userAccountService;
    private final RegistrationSessionService registrationSessionService;

    public BotResponse startRegistration(Long telegramUserId, Long chatId) {
        Optional<UserAccount> existingAccount = userAccountService.findByTelegramUserId(telegramUserId);
        if (existingAccount.isPresent()) {
            UserAccount account = existingAccount.get();
            return BotResponse.of(buildMessage(chatId,
                    "Ви вже зареєстровані. Статус: %s Роль: %s".formatted(
                            formatStatus(account.getStatus()),
                            formatRole(account.getRole()))));
        }

        RegistrationSession session = registrationSessionService.findByTelegramUserId(telegramUserId)
                .orElseGet(RegistrationSession::new);
        session.setTelegramUserId(telegramUserId);
        session.setState(RegistrationState.ASK_LAST_NAME);
        session.setDraftLastName(null);
        session.setDraftRole(null);
        registrationSessionService.save(session);

        log.info("Start registration for telegramUserId={}", telegramUserId);
        SendMessage message = buildMessage(chatId, "Вітаю! Введіть, будь ласка, ваше прізвище (2-64 символи).");
        message.setReplyMarkup(KeyboardFactory.cancelKeyboard());
        return BotResponse.of(message);
    }

    public BotResponse cancelRegistration(Long telegramUserId, Long chatId) {
        registrationSessionService.deleteByTelegramUserId(telegramUserId);
        log.info("Registration cancelled for telegramUserId={}", telegramUserId);
        return BotResponse.of(buildMessage(chatId, "Реєстрацію скасовано."));
    }

    public BotResponse handleText(Long telegramUserId, Long chatId, String text) {
        if ("/cancel".equalsIgnoreCase(text) || "Скасувати".equalsIgnoreCase(text)) {
            return cancelRegistration(telegramUserId, chatId);
        }

        Optional<RegistrationSession> sessionOptional = registrationSessionService.findByTelegramUserId(telegramUserId);
        if (sessionOptional.isEmpty()) {
            return BotResponse.of(buildMessage(chatId, "Немає активної реєстрації. Надішліть /start."));
        }

        RegistrationSession session = sessionOptional.get();
        if (session.getState() == RegistrationState.ASK_LAST_NAME) {
            String normalized = normalizeLastName(text);
            if (normalized == null) {
                SendMessage errorMessage = buildMessage(chatId, "Прізвище має містити лише літери та бути довжиною 2-64 символи.");
                errorMessage.setReplyMarkup(KeyboardFactory.cancelKeyboard());
                return BotResponse.of(errorMessage);
            }

            session.setDraftLastName(normalized);
            session.setState(RegistrationState.ASK_ROLE);
            registrationSessionService.save(session);
            log.info("Registration state transition telegramUserId={} -> ASK_ROLE", telegramUserId);

            SendMessage message = buildMessage(chatId, "Оберіть вашу роль:");
            message.setReplyMarkup(KeyboardFactory.roleKeyboard());
            return BotResponse.of(message);
        }

        SendMessage message = buildMessage(chatId, "Будь ласка, використовуйте кнопки для вибору.");
        return BotResponse.of(message);
    }

    @Transactional
    public BotResponse handleCallback(Long telegramUserId, Long chatId, String callbackData, String callbackId) {
        if (CallbackData.CANCEL.equals(callbackData)) {
            BotResponse response = cancelRegistration(telegramUserId, chatId);
            return appendCallbackAnswer(response, callbackId, "Скасовано");
        }

        Optional<RegistrationSession> sessionOptional = registrationSessionService.findByTelegramUserId(telegramUserId);
        if (sessionOptional.isEmpty()) {
            BotResponse response = BotResponse.of(buildMessage(chatId, "Сесію не знайдено. Надішліть /start."));
            return appendCallbackAnswer(response, callbackId, "Сесію не знайдено");
        }

        RegistrationSession session = sessionOptional.get();
        if (session.getState() == RegistrationState.ASK_ROLE) {
            return handleRoleSelection(session, chatId, callbackData, callbackId);
        }

        if (session.getState() == RegistrationState.ASK_LOCATION) {
            return handleLocationSelection(session, chatId, callbackData, callbackId);
        }

        BotResponse response = BotResponse.of(buildMessage(chatId, "Невідомий стан реєстрації. Надішліть /start."));
        return appendCallbackAnswer(response, callbackId, "Невідомий стан");
    }

    private BotResponse handleRoleSelection(RegistrationSession session, Long chatId, String callbackData, String callbackId) {
        Role role = resolveRole(callbackData);
        if (role == null) {
            BotResponse response = BotResponse.of(buildMessage(chatId, "Будь ласка, оберіть роль кнопками."));
            return appendCallbackAnswer(response, callbackId, "Некоректна роль");
        }

        session.setDraftRole(role);
        if (role == Role.TM) {
            UserAccount account = createUserAccount(session, chatId, null);
            registrationSessionService.deleteByTelegramUserId(session.getTelegramUserId());
            log.info("Saved user account telegramUserId={} role={} status={}", account.getTelegramUserId(),
                    account.getRole(), account.getStatus());
            BotResponse response = BotResponse.of(buildMessage(chatId,
                    "Дякую! Заявку на реєстрацію створено. Очікуйте підтвердження."));
            return appendCallbackAnswer(response, callbackId, "Збережено");
        }

        session.setState(RegistrationState.ASK_LOCATION);
        registrationSessionService.save(session);
        log.info("Registration state transition telegramUserId={} -> ASK_LOCATION", session.getTelegramUserId());

        SendMessage message = buildMessage(chatId, "Оберіть локацію:");
        message.setReplyMarkup(KeyboardFactory.locationKeyboard(LOCATIONS));
        BotResponse response = BotResponse.of(message);
        return appendCallbackAnswer(response, callbackId, "Оберіть локацію");
    }

    private BotResponse handleLocationSelection(RegistrationSession session, Long chatId, String callbackData, String callbackId) {
        if (CallbackData.BACK_TO_ROLE.equals(callbackData)) {
            session.setState(RegistrationState.ASK_ROLE);
            session.setDraftRole(null);
            registrationSessionService.save(session);
            SendMessage message = buildMessage(chatId, "Оберіть вашу роль:");
            message.setReplyMarkup(KeyboardFactory.roleKeyboard());
            BotResponse response = BotResponse.of(message);
            return appendCallbackAnswer(response, callbackId, "Назад");
        }

        if (!callbackData.startsWith(CallbackData.LOCATION_PREFIX)) {
            BotResponse response = BotResponse.of(buildMessage(chatId, "Будь ласка, оберіть локацію кнопками."));
            return appendCallbackAnswer(response, callbackId, "Некоректна локація");
        }

        Integer index = parseLocationIndex(callbackData);
        if (index == null || index < 0 || index >= LOCATIONS.size()) {
            BotResponse response = BotResponse.of(buildMessage(chatId, "Локація не знайдена. Спробуйте ще раз."));
            return appendCallbackAnswer(response, callbackId, "Локація не знайдена");
        }

        String location = LOCATIONS.get(index);
        UserAccount account = createUserAccount(session, chatId, location);
        registrationSessionService.deleteByTelegramUserId(session.getTelegramUserId());
        log.info("Saved user account telegramUserId={} role={} status={} location={}", account.getTelegramUserId(),
                account.getRole(), account.getStatus(), account.getLocation());
        BotResponse response = BotResponse.of(buildMessage(chatId,
                "Дякую! Заявку на реєстрацію створено. Очікуйте підтвердження."));
        return appendCallbackAnswer(response, callbackId, "Збережено");
    }

    private UserAccount createUserAccount(RegistrationSession session, Long chatId, String location) {
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

    private static Role resolveRole(String callbackData) {
        if (CallbackData.ROLE_SELLER.equals(callbackData)) {
            return Role.SELLER;
        }
        if (CallbackData.ROLE_SENIOR_SELLER.equals(callbackData)) {
            return Role.SENIOR_SELLER;
        }
        if (CallbackData.ROLE_TM.equals(callbackData)) {
            return Role.TM;
        }
        return null;
    }

    private static Integer parseLocationIndex(String callbackData) {
        String value = callbackData.substring(CallbackData.LOCATION_PREFIX.length());
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BotResponse appendCallbackAnswer(BotResponse response, String callbackId, String text) {
        List<BotApiMethod<?>> actions = new java.util.ArrayList<>(response.actions());
        if (callbackId != null && !callbackId.isBlank()) {
            AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackId)
                    .text(text)
                    .showAlert(false)
                    .build();
            actions.add(answer);
        }
        return new BotResponse(actions);
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
}
