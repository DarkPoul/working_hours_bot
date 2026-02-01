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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleInteractionService {

    private static final String BUTTON_EDIT = "🗓 Створити графік";
    private static final String BUTTON_VIEW = "📅 Мій графік";

    private final UserAccountService userAccountService;
    private final ScheduleService scheduleService;
    private final ScheduleDraftStore draftStore;
    private final ScheduleCalendarKeyboardBuilder keyboardBuilder;
    private final ScheduleRenderer scheduleRenderer;

    public boolean isScheduleCommand(String text) {
        return BUTTON_EDIT.equalsIgnoreCase(text) || BUTTON_VIEW.equalsIgnoreCase(text);
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

    public BotResponse handleCallback(CallbackQuery callbackQuery) {
        if (callbackQuery == null || callbackQuery.getFrom() == null) {
            return BotResponse.empty();
        }
        String data = callbackQuery.getData();
        if (data == null || data.isBlank()) {
            return BotResponse.empty();
        }
        if (data.startsWith("E:")) {
            return handleEditCallback(callbackQuery, data);
        }
        if (data.startsWith("V:")) {
            return handleViewCallback(callbackQuery, data);
        }
        return BotResponse.empty();
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
        return BotResponse.of(message);
    }

    public BotResponse startView(Long telegramUserId, Long chatId) {
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
        ScheduleDraft draft = draftStore.findDraft(telegramUserId, ScheduleMode.EDIT)
                .orElseGet(() -> new ScheduleDraft(
                        telegramUserId,
                        location.getId(),
                        YearMonth.now(),
                        ScheduleMode.EDIT,
                        scheduleService.loadWorkDays(telegramUserId, location.getId(), YearMonth.now())
                ));
        updateDraftMessageInfo(draft, callbackQuery.getMessage());
        draftStore.markChatDraft(draft.getMessageChatId(), draft);

        String[] parts = data.split(":");
        if (parts.length < 2) {
            return BotResponse.empty();
        }
        String action = parts[1];
        if ("D".equals(action) && parts.length == 3) {
            LocalDate date = LocalDate.parse(parts[2]);
            if (YearMonth.from(date).equals(draft.getYearMonth())) {
                draft.toggleDay(date);
            }
            draftStore.saveDraft(draft);
            return BotResponse.of(buildEditMessage(callbackQuery, location.getName(), draft));
        }
        if ("S".equals(action)) {
            scheduleService.saveMonth(telegramUserId, location.getId(), draft.getYearMonth(), draft.getWorkDays());
            draftStore.saveDraft(draft);
            return BotResponse.of(buildEditMessage(callbackQuery, location.getName(), draft),
                    answer(callbackQuery, "Збережено."));
        }
        if ("C".equals(action)) {
            draft.clear();
            draftStore.saveDraft(draft);
            return BotResponse.of(buildEditMessage(callbackQuery, location.getName(), draft));
        }
        if ("X".equals(action)) {
            draftStore.removeDraft(draft);
            EditMessageText edit = buildCancelEditMessage(callbackQuery);
            SendMessage menu = buildSimpleMessage(callbackQuery.getMessage().getChatId(), "Редагування скасовано.");
            menu.setReplyMarkup(KeyboardFactory.mainMenuKeyboard());
            return BotResponse.of(edit, menu);
        }
        if ("P".equals(action) || "N".equals(action)) {
            YearMonth target = "P".equals(action) ? draft.getYearMonth().minusMonths(1) : draft.getYearMonth().plusMonths(1);
            if (!ScheduleDatePolicy.isEditableMonth(target, YearMonth.now())) {
                return BotResponse.of(answer(callbackQuery, "Недоступно"));
            }
            draft.setYearMonth(target);
            draft.clear();
            draft.getWorkDays().addAll(scheduleService.loadWorkDays(telegramUserId, location.getId(), target));
            draftStore.saveDraft(draft);
            return BotResponse.of(buildEditMessage(callbackQuery, location.getName(), draft));
        }
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
        ScheduleDraft draft = draftStore.findDraft(telegramUserId, ScheduleMode.VIEW)
                .orElseGet(() -> new ScheduleDraft(
                        telegramUserId,
                        location.getId(),
                        YearMonth.now(),
                        ScheduleMode.VIEW,
                        scheduleService.loadWorkDays(telegramUserId, location.getId(), YearMonth.now())
                ));
        updateDraftMessageInfo(draft, callbackQuery.getMessage());
        draftStore.markChatDraft(draft.getMessageChatId(), draft);

        String[] parts = data.split(":");
        if (parts.length < 2) {
            return BotResponse.empty();
        }
        String action = parts[1];
        if ("P".equals(action) || "N".equals(action)) {
            YearMonth target = "P".equals(action) ? draft.getYearMonth().minusMonths(1) : draft.getYearMonth().plusMonths(1);
            if (!ScheduleDatePolicy.isViewableMonth(target, YearMonth.now())) {
                return BotResponse.of(answer(callbackQuery, "Недоступно"));
            }
            draft.setYearMonth(target);
            draft.clear();
            draft.getWorkDays().addAll(scheduleService.loadWorkDays(telegramUserId, location.getId(), target));
            draftStore.saveDraft(draft);
            return BotResponse.of(buildViewMessage(callbackQuery, location.getName(), draft));
        }
        return BotResponse.empty();
    }

    private void updateDraftMessageInfo(ScheduleDraft draft, Message message) {
        if (message == null) {
            return;
        }
        draft.setMessageChatId(message.getChatId());
        draft.setMessageId(message.getMessageId());
    }

    private SendMessage buildEditMessage(Long chatId, String locationName, YearMonth month, Set<LocalDate> workDays) {
        SendMessage message = buildSimpleMessage(chatId,
                "Графік для локації: " + locationName + "\nОберіть робочі дні місяця:");
        message.setReplyMarkup(keyboardBuilder.buildEditKeyboard(month, workDays));
        return message;
    }

    private EditMessageText buildEditMessage(CallbackQuery callbackQuery, String locationName, ScheduleDraft draft) {
        Message message = callbackQuery.getMessage();
        EditMessageText edit = new EditMessageText();
        edit.setChatId(message.getChatId().toString());
        edit.setMessageId(message.getMessageId());
        edit.setText("Графік для локації: " + locationName + "\nОберіть робочі дні місяця:");
        edit.setReplyMarkup(keyboardBuilder.buildEditKeyboard(draft.getYearMonth(), draft.getWorkDays()));
        return edit;
    }

    private SendMessage buildViewMessage(Long chatId, String locationName, YearMonth month, Set<LocalDate> workDays) {
        String text = scheduleRenderer.renderMonthTable(locationName, month, workDays);
        SendMessage message = buildSimpleMessage(chatId, text);
        message.setParseMode("HTML");
        message.setReplyMarkup(keyboardBuilder.buildViewKeyboard(month));
        return message;
    }

    private EditMessageText buildViewMessage(CallbackQuery callbackQuery, String locationName, ScheduleDraft draft) {
        Message message = callbackQuery.getMessage();
        EditMessageText edit = new EditMessageText();
        edit.setChatId(message.getChatId().toString());
        edit.setMessageId(message.getMessageId());
        edit.setText(scheduleRenderer.renderMonthTable(locationName, draft.getYearMonth(), draft.getWorkDays()));
        edit.setParseMode("HTML");
        edit.setReplyMarkup(keyboardBuilder.buildViewKeyboard(draft.getYearMonth()));
        return edit;
    }

    private EditMessageText buildCancelEditMessage(CallbackQuery callbackQuery) {
        Message message = callbackQuery.getMessage();
        EditMessageText edit = new EditMessageText();
        edit.setChatId(message.getChatId().toString());
        edit.setMessageId(message.getMessageId());
        edit.setText("Редагування скасовано.");
        return edit;
    }

    private SendMessage buildSimpleMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        return message;
    }

    private AnswerCallbackQuery answer(CallbackQuery callbackQuery, String text) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQuery.getId());
        answer.setText(text);
        return answer;
    }
}
