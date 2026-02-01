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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleInteractionHandler {

    private static final String COMMAND_EDIT = "✍️ Внести графік";
    private static final String COMMAND_VIEW = "🗓 Мій графік";

    private static final String BTN_CLEAR = "🗑 Очистити";
    private static final String BTN_BACK = "⬅️ Назад";
    private static final String BTN_PREV = "◀️ Попередній місяць";
    private static final String BTN_NEXT = "▶️ Наступний місяць";
    private static final String BTN_SAVE = "💾 Зберегти";

    private final UserAccountService userAccountService;
    private final SchedulePersistenceService schedulePersistenceService;
    private final ScheduleService scheduleService;
    private final ScheduleSessionStore sessionStore;
    private final ScheduleCalendarRenderer calendarRenderer;
    private final ScheduleCalendarKeyboardBuilder keyboardBuilder;
    private final ScheduleRenderer scheduleRenderer;

    public BotResponse handleMessage(Long telegramUserId, Long chatId, String text) {
        if (text == null || text.isBlank()) {
            return BotResponse.empty();
        }

        Optional<UserAccount> accountOptional = userAccountService.findByTelegramUserId(telegramUserId);
        if (accountOptional.isEmpty()) {
            return BotResponse.empty();
        }
        UserAccount account = accountOptional.get();
        if (account.getStatus() != RegistrationStatus.APPROVED || account.getRole() == Role.TM) {
            return BotResponse.empty();
        }

        ScheduleSession session = sessionStore.getOrCreate(telegramUserId);

        if (isEditMenuAction(text)) {
            if (session.getMode() != InteractionMode.EDIT_SCHEDULE) {
                SendMessage response = simpleMessage(chatId, "Спочатку натисніть «Внести графік».");
                response.setReplyMarkup(KeyboardFactory.mainMenuKeyboard());
                return BotResponse.of(response);
            }
            return handleEditMenuAction(session, account, chatId, text);
        }

        if (COMMAND_EDIT.equalsIgnoreCase(text)) {
            return enterEditMode(session, account, chatId);
        }

        if (COMMAND_VIEW.equalsIgnoreCase(text)) {
            return showView(session, account, chatId);
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

    private BotResponse enterEditMode(ScheduleSession session, UserAccount account, Long chatId) {
        Location location = account.getLocation();
        if (location == null) {
            return BotResponse.of(simpleMessage(chatId, "Спочатку оберіть локацію."));
        }

        YearMonth month = YearMonth.now();
        Set<LocalDate> workDays = schedulePersistenceService.loadMonth(
                account.getTelegramUserId(),
                location.getId(),
                month
        );

        session.setMode(InteractionMode.EDIT_SCHEDULE);
        session.setActiveLocationId(location.getId());
        session.setActiveYearMonth(month);
        session.clearDrafts();
        session.getOrCreateDraft(month).addAll(workDays);
        session.setCalendarChatId(chatId);
        session.setCalendarMessageId(null);

        log.info("Enter edit mode. userId={}, locationId={}, month={}",
                account.getTelegramUserId(), location.getId(), month);

        SendMessage calendarMessage = calendarRenderer.buildEditMessage(chatId, location.getName(), month, workDays);
        sessionStore.markPendingCalendarMessage(chatId, account.getTelegramUserId());

        SendMessage menuMessage = simpleMessage(chatId, "Режим редагування графіка.");
        menuMessage.setReplyMarkup(KeyboardFactory.scheduleEditMenuKeyboard());

        return BotResponse.of(calendarMessage, menuMessage);
    }

    private BotResponse showView(ScheduleSession session, UserAccount account, Long chatId) {
        Location location = account.getLocation();
        if (location == null) {
            return BotResponse.of(simpleMessage(chatId, "Спочатку оберіть локацію."));
        }

        YearMonth month = YearMonth.now();
        Set<LocalDate> workDays = schedulePersistenceService.loadMonth(
                account.getTelegramUserId(),
                location.getId(),
                month
        );

        session.setMode(InteractionMode.VIEW_SCHEDULE);
        session.setActiveLocationId(location.getId());
        session.setActiveYearMonth(month);
        session.setCalendarChatId(chatId);
        session.setCalendarMessageId(null);

        log.info("Enter view mode. userId={}, locationId={}, month={}",
                account.getTelegramUserId(), location.getId(), month);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        ScheduleService.ScheduleSummary summary = scheduleService.getMonthSummary(
                account.getTelegramUserId(),
                month.getYear(),
                month.getMonthValue()
        );
        message.setText(scheduleRenderer.renderMonthTable(location.getName(), month, workDays, summary));
        message.setParseMode("HTML");
        message.setReplyMarkup(keyboardBuilder.buildViewKeyboard(month));

        sessionStore.markPendingCalendarMessage(chatId, account.getTelegramUserId());
        return BotResponse.of(message);
    }

    private BotResponse handleEditMenuAction(ScheduleSession session, UserAccount account, Long chatId, String text) {
        Location location = account.getLocation();
        if (location == null) {
            return BotResponse.of(simpleMessage(chatId, "Спочатку оберіть локацію."));
        }

        YearMonth activeMonth = session.getActiveYearMonth();
        if (activeMonth == null) {
            activeMonth = YearMonth.now();
            session.setActiveYearMonth(activeMonth);
        }

        Set<LocalDate> draftDays = session.getOrCreateDraft(activeMonth);

        if (BTN_CLEAR.equals(text)) {
            draftDays.clear();
            log.info("Clear schedule draft. userId={}, locationId={}, month={}",
                    account.getTelegramUserId(), location.getId(), activeMonth);
            return BotResponse.of(buildCalendarUpdate(session, chatId, location.getName(), activeMonth, draftDays));
        }

        if (BTN_SAVE.equals(text)) {
            log.info("Save schedule draft. userId={}, locationId={}, month={}, count={}",
                    account.getTelegramUserId(), location.getId(), activeMonth, draftDays.size());

            schedulePersistenceService.saveMonth(
                    account.getTelegramUserId(),
                    location.getId(),
                    activeMonth,
                    draftDays
            );

            Set<LocalDate> persistedDays = schedulePersistenceService.loadMonth(
                    account.getTelegramUserId(),
                    location.getId(),
                    activeMonth
            );

            draftDays.clear();
            draftDays.addAll(persistedDays);

            BotApiMethod<?> calendarUpdate = buildCalendarUpdate(
                    session,
                    chatId,
                    location.getName(),
                    activeMonth,
                    draftDays
            );

            SendMessage notice = simpleMessage(chatId, "Збережено ✅");
            return BotResponse.of(calendarUpdate, notice);
        }

        if (BTN_PREV.equals(text) || BTN_NEXT.equals(text)) {
            YearMonth target = BTN_PREV.equals(text)
                    ? activeMonth.minusMonths(1)
                    : activeMonth.plusMonths(1);
            if (!ScheduleDatePolicy.isEditableMonth(target, YearMonth.now())) {
                return BotResponse.of(simpleMessage(chatId, "Недоступно"));
            }

            session.setActiveYearMonth(target);
            boolean hasDraft = session.hasDraft(target);
            Set<LocalDate> targetDays = session.getOrCreateDraft(target);
            if (!hasDraft) {
                targetDays.addAll(schedulePersistenceService.loadMonth(
                        account.getTelegramUserId(),
                        location.getId(),
                        target
                ));
            }

            log.info("Switch edit month. userId={}, locationId={}, month={}",
                    account.getTelegramUserId(), location.getId(), target);

            return BotResponse.of(buildCalendarUpdate(session, chatId, location.getName(), target, targetDays));
        }

        if (BTN_BACK.equals(text)) {
            log.info("Exit edit mode. userId={}, locationId={}",
                    account.getTelegramUserId(), location.getId());

            session.setMode(InteractionMode.NORMAL);
            session.setActiveYearMonth(null);
            session.setActiveLocationId(null);
            session.clearDrafts();

            BotApiMethod<?> calendarUpdate = buildCancelEditMessage(session);
            SendMessage menu = simpleMessage(chatId, "Редагування скасовано.");
            menu.setReplyMarkup(KeyboardFactory.mainMenuKeyboard());

            if (calendarUpdate == null) {
                return BotResponse.of(menu);
            }
            return BotResponse.of((BotApiMethod<?>) calendarUpdate, menu);
        }

        return BotResponse.empty();
    }

    private BotResponse handleEditCallback(CallbackQuery callbackQuery, String data) {
        Long telegramUserId = callbackQuery.getFrom().getId();
        ScheduleSession session = sessionStore.find(telegramUserId).orElse(null);
        if (session == null || session.getMode() != InteractionMode.EDIT_SCHEDULE) {
            return BotResponse.of(answer(callbackQuery, "Спочатку натисніть «Внести графік»."));
        }

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

        updateSessionMessageInfo(session, callbackQuery.getMessage());

        String[] parts = data.split(":");
        if (parts.length < 3) {
            return BotResponse.empty();
        }

        if (!"D".equals(parts[1])) {
            return BotResponse.empty();
        }

        LocalDate date;
        try {
            date = LocalDate.parse(parts[2]);
        } catch (Exception ex) {
            log.warn("Failed to parse date from callback data: {}", data, ex);
            return BotResponse.empty();
        }

        YearMonth targetMonth = YearMonth.from(date);
        if (!ScheduleDatePolicy.isEditableMonth(targetMonth, YearMonth.now())) {
            return BotResponse.of(answer(callbackQuery, "Недоступно"));
        }

        session.setActiveYearMonth(targetMonth);
        boolean hasDraft = session.hasDraft(targetMonth);
        Set<LocalDate> draftDays = session.getOrCreateDraft(targetMonth);
        if (!hasDraft) {
            draftDays.addAll(schedulePersistenceService.loadMonth(
                    telegramUserId,
                    location.getId(),
                    targetMonth
            ));
        }
        boolean isWork = toggleDay(draftDays, date);

        log.info("Toggle day. userId={}, date={}, isWork={}",
                telegramUserId, date, isWork);

        EditMessageText edit = calendarRenderer.buildEditMessage(
                session.getCalendarChatId(),
                session.getCalendarMessageId(),
                location.getName(),
                targetMonth,
                draftDays
        );
        return BotResponse.of(edit);
    }

    private BotResponse handleViewCallback(CallbackQuery callbackQuery, String data) {
        Long telegramUserId = callbackQuery.getFrom().getId();
        ScheduleSession session = sessionStore.find(telegramUserId).orElse(null);
        if (session == null) {
            return BotResponse.empty();
        }

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

        updateSessionMessageInfo(session, callbackQuery.getMessage());

        String[] parts = data.split(":");
        if (parts.length < 2) {
            return BotResponse.empty();
        }

        String action = parts[1];
        if (!"P".equals(action) && !"N".equals(action)) {
            return BotResponse.empty();
        }

        YearMonth current = session.getActiveYearMonth() == null ? YearMonth.now() : session.getActiveYearMonth();
        YearMonth target = "P".equals(action) ? current.minusMonths(1) : current.plusMonths(1);

        if (!ScheduleDatePolicy.isViewableMonth(target, YearMonth.now())) {
            return BotResponse.of(answer(callbackQuery, "Недоступно"));
        }

        session.setMode(InteractionMode.VIEW_SCHEDULE);
        session.setActiveYearMonth(target);

        Set<LocalDate> workDays = schedulePersistenceService.loadMonth(
                telegramUserId,
                location.getId(),
                target
        );

        EditMessageText edit = new EditMessageText();
        edit.setChatId(session.getCalendarChatId().toString());
        edit.setMessageId(session.getCalendarMessageId());
        ScheduleService.ScheduleSummary summary = scheduleService.getMonthSummary(
                telegramUserId,
                target.getYear(),
                target.getMonthValue()
        );
        edit.setText(scheduleRenderer.renderMonthTable(location.getName(), target, workDays, summary));
        edit.setParseMode("HTML");
        edit.setReplyMarkup(keyboardBuilder.buildViewKeyboard(target));

        return BotResponse.of(edit);
    }

    private BotApiMethod<?> buildCalendarUpdate(ScheduleSession session,
                                                Long chatId,
                                                String locationName,
                                                YearMonth month,
                                                Set<LocalDate> workDays) {
        if (session.getCalendarChatId() != null && session.getCalendarMessageId() != null) {
            return calendarRenderer.buildEditMessage(
                    session.getCalendarChatId(),
                    session.getCalendarMessageId(),
                    locationName,
                    month,
                    workDays
            );
        }

        session.setCalendarChatId(chatId);
        session.setCalendarMessageId(null);
        sessionStore.markPendingCalendarMessage(chatId, session.getTelegramUserId());
        return calendarRenderer.buildEditMessage(chatId, locationName, month, workDays);
    }

    private BotApiMethod<?> buildCancelEditMessage(ScheduleSession session) {
        if (session.getCalendarChatId() == null || session.getCalendarMessageId() == null) {
            return null;
        }
        EditMessageText edit = new EditMessageText();
        edit.setChatId(session.getCalendarChatId().toString());
        edit.setMessageId(session.getCalendarMessageId());
        edit.setText("Редагування скасовано.");
        return edit;
    }

    private boolean toggleDay(Set<LocalDate> draftDays, LocalDate date) {
        if (draftDays.contains(date)) {
            draftDays.remove(date);
            return false;
        }
        draftDays.add(date);
        return true;
    }

    private boolean isEditMenuAction(String text) {
        return BTN_CLEAR.equals(text)
                || BTN_BACK.equals(text)
                || BTN_PREV.equals(text)
                || BTN_NEXT.equals(text)
                || BTN_SAVE.equals(text);
    }

    private SendMessage simpleMessage(Long chatId, String text) {
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .build();
    }

    private void updateSessionMessageInfo(ScheduleSession session, Message message) {
        if (message == null) {
            return;
        }
        session.setCalendarChatId(message.getChatId());
        session.setCalendarMessageId(message.getMessageId());
    }

    private BotApiMethod<?> answer(CallbackQuery callbackQuery, String text) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQuery.getId());
        answer.setText(text);
        return answer;
    }
}
