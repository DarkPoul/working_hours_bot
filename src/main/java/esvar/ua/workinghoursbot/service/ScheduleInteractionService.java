package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.bot.BotResponse;
import esvar.ua.workinghoursbot.bot.KeyboardFactory;
import esvar.ua.workinghoursbot.domain.Location;
import esvar.ua.workinghoursbot.domain.RegistrationStatus;
import esvar.ua.workinghoursbot.domain.Role;
import esvar.ua.workinghoursbot.domain.UserAccount;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ScheduleInteractionService {

    private static final String BUTTON_EDIT = "🗓 Створити графік";
    private static final String BUTTON_VIEW = "📅 Мій графік";

    // нові текстові кнопки з меню
    private static final String BTN_CLEAR = "🗑 Очистити";
    private static final String BTN_BACK = "⬅️ Назад";
    private static final String BTN_PREV = "◀️";
    private static final String BTN_NEXT = "▶️";
    private static final String BTN_SAVE = "💾 Зберегти";

    private final UserAccountService userAccountService;
    private final ScheduleService scheduleService;
    private final ScheduleDraftStore draftStore;
    private final ScheduleCalendarKeyboardBuilder keyboardBuilder;
    private final ScheduleRenderer scheduleRenderer;

    public boolean isScheduleCommand(String text) {
        return BUTTON_EDIT.equalsIgnoreCase(text) || BUTTON_VIEW.equalsIgnoreCase(text);
    }

    public boolean isScheduleEditMenuCommand(String text) {
        return BTN_CLEAR.equals(text)
                || BTN_BACK.equals(text)
                || BTN_PREV.equals(text)
                || BTN_NEXT.equals(text)
                || BTN_SAVE.equals(text);
    }

    public BotResponse handleMenuCommand(Long telegramUserId, Long chatId, String text) {
        if (BUTTON_EDIT.equalsIgnoreCase(text)) {
            return startEdit(telegramUserId, chatId);
        }
        if (BUTTON_VIEW.equalsIgnoreCase(text)) {
            return startView(telegramUserId, chatId);
        }
        return BotResponse.empty();
    }

    private BotResponse startView(Long telegramUserId, Long chatId) {
        Optional<UserAccount> accountOptional = userAccountService.findByTelegramUserId(telegramUserId);
        if (accountOptional.isEmpty()) {
            return BotResponse.empty();
        }
        UserAccount account = accountOptional.get();
        if (account.getStatus() != RegistrationStatus.APPROVED || account.getRole() == Role.TM) {
            return BotResponse.empty();
        }
        Location location = account.getLocation();
        if (location == null) {
            return BotResponse.of(buildSimpleMessage(chatId, "Спочатку оберіть локацію."));
        }
        YearMonth month = YearMonth.now();
        Set<LocalDate> workDays = scheduleService.loadWorkDays(telegramUserId, location.getId(), month);
        ScheduleDraft draft = new ScheduleDraft(telegramUserId, location.getId(), month, ScheduleMode.VIEW, workDays);
        draft.setMessageChatId(chatId);
        draftStore.saveDraft(draft);
        draftStore.markChatDraft(chatId, draft);
        SendMessage message = buildViewMessage(chatId, location.getName(), month, workDays);
        return BotResponse.of(message);
    }

    private SendMessage buildViewMessage(Long chatId, String name, YearMonth month, Set<LocalDate> workDays) {
        String text = scheduleRenderer.renderMonthTable(name, month, workDays);
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("HTML") // бо renderMonthTable повертає текст з <pre> та іншою розміткою
                .build();
    }

    public BotResponse handleEditMenuCommand(Long telegramUserId, Long chatId, String text) {
        Optional<UserAccount> accountOptional = userAccountService.findByTelegramUserId(telegramUserId);
        if (accountOptional.isEmpty()) {
            return BotResponse.empty();
        }
        UserAccount account = accountOptional.get();
        if (account.getStatus() != RegistrationStatus.APPROVED || account.getRole() == Role.TM) {
            return BotResponse.empty();
        }
        Location location = account.getLocation();
        if (location == null) {
            return BotResponse.of(buildSimpleMessage(chatId, "Спочатку оберіть локацію."));
        }

        ScheduleDraft draft = draftStore.findDraft(telegramUserId, ScheduleMode.EDIT)
                .orElseGet(() -> new ScheduleDraft(
                        telegramUserId,
                        location.getId(),
                        YearMonth.now(),
                        ScheduleMode.EDIT,
                        scheduleService.loadWorkDays(telegramUserId, location.getId(), YearMonth.now())
                ));

        // У draft вже мають бути chatId та messageId з startEdit / callback
        if (BTN_CLEAR.equals(text)) {
            draft.clear();
            draftStore.saveDraft(draft);
            EditMessageText edit = buildEditMessageFromDraft(location.getName(), draft);
            return BotResponse.of(edit);
        }

        if (BTN_SAVE.equals(text)) {
            try {
                log.debug("Saving schedule draft via reply menu. userId={}, locationId={}, month={}, workDaysCount={}",
                        telegramUserId, location.getId(), draft.getYearMonth(), draft.getWorkDays().size());
                scheduleService.saveMonth(telegramUserId, location.getId(), draft.getYearMonth(), draft.getWorkDays());
                Set<LocalDate> persistedDays = scheduleService.loadWorkDays(
                        telegramUserId,
                        location.getId(),
                        draft.getYearMonth()
                );
                draft.clear();
                draft.getWorkDays().addAll(persistedDays);
                draftStore.saveDraft(draft);
                EditMessageText edit = buildEditMessageFromDraft(location.getName(), draft);
                SendMessage notice = buildSimpleMessage(chatId, "Збережено ✅");
                return BotResponse.of(edit, notice);
            } catch (Exception ex) {
                log.error("Failed to save schedule month via reply menu. userId={}, locationId={}, month={}",
                        telegramUserId, location.getId(), draft.getYearMonth(), ex);
                return BotResponse.of(buildSimpleMessage(chatId, "Не вдалося зберегти графік."));
            }
        }

        if (BTN_PREV.equals(text) || BTN_NEXT.equals(text)) {
            YearMonth target = BTN_PREV.equals(text)
                    ? draft.getYearMonth().minusMonths(1)
                    : draft.getYearMonth().plusMonths(1);
            if (!ScheduleDatePolicy.isEditableMonth(target, YearMonth.now())) {
                return BotResponse.of(buildSimpleMessage(chatId, "Недоступно"));
            }
            draft.setYearMonth(target);
            draft.clear();
            draft.getWorkDays().addAll(scheduleService.loadWorkDays(telegramUserId, location.getId(), target));
            draftStore.saveDraft(draft);
            EditMessageText edit = buildEditMessageFromDraft(location.getName(), draft);
            return BotResponse.of(edit);
        }

        if (BTN_BACK.equals(text)) {
            draftStore.removeDraft(draft);
            EditMessageText edit = buildCancelEditMessageFromDraft(draft);
            SendMessage menu = buildSimpleMessage(chatId, "Редагування скасовано.");
            menu.setReplyMarkup(KeyboardFactory.mainMenuKeyboard());
            return BotResponse.of(edit, menu);
        }

        return BotResponse.empty();
    }

    private SendMessage buildSimpleMessage(Long chatId, String text) {
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .build();
    }

    public BotResponse startEdit(Long telegramUserId, Long chatId) {
        Optional<UserAccount> accountOptional = userAccountService.findByTelegramUserId(telegramUserId);
        if (accountOptional.isEmpty()) {
            return BotResponse.empty();
        }
        UserAccount account = accountOptional.get();
        if (account.getStatus() != RegistrationStatus.APPROVED || account.getRole() == Role.TM) {
            return BotResponse.empty();
        }
        Location location = account.getLocation();
        if (location == null) {
            return BotResponse.of(buildSimpleMessage(chatId, "Спочатку оберіть локацію."));
        }
        YearMonth month = YearMonth.now();
        Set<LocalDate> workDays = scheduleService.loadWorkDays(telegramUserId, location.getId(), month);
        ScheduleDraft draft = new ScheduleDraft(telegramUserId, location.getId(), month, ScheduleMode.EDIT, workDays);
        draft.setMessageChatId(chatId);
        draftStore.saveDraft(draft);
        draftStore.markChatDraft(chatId, draft);
        SendMessage message = buildEditMessage(chatId, location.getName(), month, workDays);
        // показуємо меню керування графіком
        message.setReplyMarkup(KeyboardFactory.scheduleEditMenuKeyboard());
        return BotResponse.of(message);
    }

    // ... решта існуючих методів handleCallback, startView тощо без змін ...

    private EditMessageText buildEditMessageFromDraft(String locationName, ScheduleDraft draft) {
        EditMessageText edit = new EditMessageText();
        edit.setChatId(draft.getMessageChatId().toString());
        edit.setMessageId(draft.getMessageId());
        edit.setText("Графік для локації: " + locationName + "\nОберіть робочі дні місяця:");
        edit.setReplyMarkup(keyboardBuilder.buildEditKeyboard(draft.getYearMonth(), draft.getWorkDays()));
        return edit;
    }

    private EditMessageText buildCancelEditMessageFromDraft(ScheduleDraft draft) {
        EditMessageText edit = new EditMessageText();
        edit.setChatId(draft.getMessageChatId().toString());
        edit.setMessageId(draft.getMessageId());
        edit.setText("Редагування скасовано.");
        return edit;
    }

    private SendMessage buildEditMessage(Long chatId, String locationName, YearMonth month, Set<LocalDate> workDays) {
        SendMessage message = buildSimpleMessage(chatId,
                "Графік для локації: " + locationName + "\nОберіть робочі дні місяця:");
        message.setReplyMarkup(keyboardBuilder.buildEditKeyboard(month, workDays));
        return message;
    }

    public BotResponse handleCallback(CallbackQuery callbackQuery) {
        if (callbackQuery == null || callbackQuery.getFrom() == null) {
            return BotResponse.empty();
        }

        String data = callbackQuery.getData();
        if (data == null || data.isBlank()) {
            return BotResponse.empty();
        }

        if (data.startsWith("E:")) {
            // callback-и для редагування графіка
            return handleEditCallback(callbackQuery, data);
        }

        if (data.startsWith("V:")) {
            // callback-и для перегляду графіка
            return handleViewCallback(callbackQuery, data);
        }

        // інші callback-и для цього сервісу не обробляємо
        return BotResponse.empty();
    }

    private BotResponse handleEditCallback(CallbackQuery callbackQuery, String data) {
        Long telegramUserId = callbackQuery.getFrom().getId();

        Optional<UserAccount> accountOptional = userAccountService.findByTelegramUserId(telegramUserId);
        if (accountOptional.isEmpty()) {
            return BotResponse.empty();
        }

        UserAccount account = accountOptional.get();
        if (account.getStatus() != RegistrationStatus.APPROVED || account.getRole() == Role.TM) {
            return BotResponse.empty();
        }

        Location location = account.getLocation();
        if (location == null) {
            return BotResponse.of(answer(callbackQuery, "Спочатку оберіть локацію."));
        }

        // шукаємо або створюємо драфт редагування графіка
        ScheduleDraft draft = draftStore.findDraft(telegramUserId, ScheduleMode.EDIT)
                .orElseGet(() -> new ScheduleDraft(
                        telegramUserId,
                        location.getId(),
                        YearMonth.now(),
                        ScheduleMode.EDIT,
                        scheduleService.loadWorkDays(telegramUserId, location.getId(), YearMonth.now())
                ));

        // оновлюємо інформацію про повідомлення, в якому показується календар редагування
        updateDraftMessageInfo(draft, callbackQuery.getMessage());
        draftStore.markChatDraft(draft.getMessageChatId(), draft);

        String[] parts = data.split(":");
        if (parts.length < 3) {
            return BotResponse.empty();
        }

        String action = parts[1];

        // натискання на конкретний день
        if ("D".equals(action)) {
            LocalDate date;
            try {
                date = LocalDate.parse(parts[2]);
            } catch (Exception ex) {
                log.warn("Failed to parse date from edit callback: data={}", data, ex);
                return BotResponse.empty();
            }

            YearMonth targetMonth = YearMonth.from(date);
            if (!ScheduleDatePolicy.isEditableMonth(targetMonth, YearMonth.now())) {
                return BotResponse.of(answer(callbackQuery, "Недоступно"));
            }

            // працюємо лише з поточним місяцем драфта
            if (!targetMonth.equals(draft.getYearMonth())) {
                draft.setYearMonth(targetMonth);
                draft.clear();
            }

            draft.toggleDay(date);
            draftStore.saveDraft(draft);

            EditMessageText edit = buildEditMessageFromDraft(location.getName(), draft);
            return BotResponse.of(edit);
        }

        // інші дії для редагування не підтримуються
        return BotResponse.empty();
    }

    private BotResponse handleViewCallback(CallbackQuery callbackQuery, String data) {
        Long telegramUserId = callbackQuery.getFrom().getId();

        Optional<UserAccount> accountOptional = userAccountService.findByTelegramUserId(telegramUserId);
        if (accountOptional.isEmpty()) {
            return BotResponse.empty();
        }

        UserAccount account = accountOptional.get();
        if (account.getStatus() != RegistrationStatus.APPROVED || account.getRole() == Role.TM) {
            return BotResponse.empty();
        }

        Location location = account.getLocation();
        if (location == null) {
            return BotResponse.of(answer(callbackQuery, "Спочатку оберіть локацію."));
        }

        // шукаємо або створюємо драфт перегляду графіка
        ScheduleDraft draft = draftStore.findDraft(telegramUserId, ScheduleMode.VIEW)
                .orElseGet(() -> new ScheduleDraft(
                        telegramUserId,
                        location.getId(),
                        YearMonth.now(),
                        ScheduleMode.VIEW,
                        scheduleService.loadWorkDays(telegramUserId, location.getId(), YearMonth.now())
                ));

        // оновлюємо інформацію про повідомлення, в якому показується графік
        updateDraftMessageInfo(draft, callbackQuery.getMessage());
        draftStore.markChatDraft(draft.getMessageChatId(), draft);

        String[] parts = data.split(":");
        if (parts.length < 2) {
            return BotResponse.empty();
        }

        String action = parts[1];

        // переміщення по місяцях
        if ("P".equals(action) || "N".equals(action)) {
            YearMonth target = "P".equals(action)
                    ? draft.getYearMonth().minusMonths(1)
                    : draft.getYearMonth().plusMonths(1);

            if (!ScheduleDatePolicy.isViewableMonth(target, YearMonth.now())) {
                return BotResponse.of(answer(callbackQuery, "Недоступно"));
            }

            draft.setYearMonth(target);
            draft.clear();
            draft.getWorkDays().addAll(
                    scheduleService.loadWorkDays(telegramUserId, location.getId(), target)
            );
            draftStore.saveDraft(draft);

            // оновлюємо існуюче повідомлення з графіком
            EditMessageText edit = buildViewMessage(callbackQuery, location.getName(), draft);
            return BotResponse.of(edit);
        }

        // інші дії для перегляду не підтримуються
        return BotResponse.empty();
    }

    private void updateDraftMessageInfo(ScheduleDraft draft, Message message) {
    }

    private EditMessageText buildViewMessage(CallbackQuery callbackQuery, String name, ScheduleDraft draft) {
        String text = scheduleRenderer.renderMonthTable(name, draft.getYearMonth(), draft.getWorkDays());

        EditMessageText edit = new EditMessageText();
        edit.setChatId(callbackQuery.getMessage().getChatId().toString());
        edit.setMessageId(callbackQuery.getMessage().getMessageId());
        edit.setText(text);
        edit.setParseMode("HTML"); // бо renderMonthTable повертає текст з <pre> тощо
        edit.setReplyMarkup(keyboardBuilder.buildViewKeyboard(draft.getYearMonth()));
        return edit;
    }

    private org.telegram.telegrambots.meta.api.methods.BotApiMethod<?> answer(CallbackQuery callbackQuery, String text) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQuery.getId());
        answer.setText(text);
        return answer;
    }


    // існуючі buildEditMessage(CallbackQuery...), buildViewMessage(...), answer(...), тощо
}
