package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.bot.BotResponse;
import esvar.ua.workinghoursbot.bot.KeyboardFactory;
import esvar.ua.workinghoursbot.domain.AuditEventType;
import esvar.ua.workinghoursbot.domain.Location;
import esvar.ua.workinghoursbot.domain.RegistrationStatus;
import esvar.ua.workinghoursbot.domain.Role;
import esvar.ua.workinghoursbot.domain.TmSession;
import esvar.ua.workinghoursbot.domain.TmState;
import esvar.ua.workinghoursbot.domain.UserAccount;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

@Service
@RequiredArgsConstructor
@Transactional
public class TmMenuService {

    private static final String BUTTON_REQUESTS = "Заявки";
    private static final String BUTTON_LOCATIONS = "Локації";
    private static final String BUTTON_SCHEDULE = "Графік локацій";
    private static final String BUTTON_ADD_LOCATION = "Додати локацію";
    private static final String BUTTON_DELETE_LOCATION = "Видалити локацію";
    private static final String BUTTON_BACK = "Назад";
    private static final String BUTTON_YES = "Так";
    private static final String BUTTON_REJECT = "Заборонити";
    private static final String BUTTON_ENABLE_SCHEDULE = "Дозволити внесення графіку";
    private static final String BUTTON_DISABLE_SCHEDULE = "Заборонити внесення графіку";
    private static final String TM_SCHEDULE_CALLBACK = "TM_SCHED:";

    private final RegistrationRequestService registrationRequestService;
    private final LocationService locationService;
    private final TmSessionService tmSessionService;
    private final TmScheduleCalendarService tmScheduleCalendarService;
    private final TmScheduleEditGateService scheduleEditGateService;
    private final AuditService auditService;
    private final UserAccountService userAccountService;

    public BotResponse showMainMenu(Long telegramUserId, Long chatId) {
        TmSession session = getOrCreateSession(telegramUserId);
        session.setState(TmState.MAIN_MENU);
        clearSelections(session);
        tmSessionService.save(session);

        SendMessage message = buildMessage(chatId, "Оберіть дію:");
        message.setReplyMarkup(KeyboardFactory.tmMainMenuKeyboard());
        return BotResponse.of(message);
    }

    public BotResponse handleText(Long telegramUserId, Long chatId, String text) {
        TmSession session = getOrCreateSession(telegramUserId);
        if (text == null) {
            return BotResponse.empty();
        }

        if (BUTTON_REQUESTS.equalsIgnoreCase(text)) {
            return showRequestsList(session, chatId);
        }
        if (BUTTON_LOCATIONS.equalsIgnoreCase(text)) {
            return showLocationsMenu(session, chatId);
        }
        if (BUTTON_SCHEDULE.equalsIgnoreCase(text)) {
            return showScheduleLocationsList(session, chatId);
        }

        return switch (session.getState()) {
            case REQUESTS_LIST -> handleRequestsList(session, chatId, text);
            case REQUEST_DETAILS -> handleRequestDetails(session, chatId, text);
            case LOCATIONS_MENU -> handleLocationsMenu(session, chatId, text);
            case LOCATION_ADD_INPUT -> handleLocationAddInput(session, chatId, text);
            case LOCATION_ADD_CONFIRM -> handleLocationAddConfirm(session, chatId, text);
            case LOCATION_DELETE_LIST -> handleLocationDeleteList(session, chatId, text);
            case LOCATION_DELETE_CONFIRM -> handleLocationDeleteConfirm(session, chatId, text);
            case SCHEDULE_LOCATIONS_LIST -> handleScheduleLocationsList(session, chatId, text);
            case SCHEDULE_LOCATION_VIEW -> handleScheduleLocationView(session, chatId, text);
            case MAIN_MENU -> showMainMenu(telegramUserId, chatId);
        };
    }

    public BotResponse handleCallback(CallbackQuery callbackQuery) {
        if (callbackQuery == null || callbackQuery.getFrom() == null) {
            return BotResponse.empty();
        }
        String data = callbackQuery.getData();
        if (data == null || !data.startsWith(TM_SCHEDULE_CALLBACK)) {
            return BotResponse.empty();
        }
        UserAccount tm = userAccountService.findByTelegramUserId(callbackQuery.getFrom().getId()).orElse(null);
        if (tm == null || tm.getRole() != Role.TM || tm.getStatus() != RegistrationStatus.APPROVED) {
            return BotResponse.empty();
        }
        String payload = data.substring(TM_SCHEDULE_CALLBACK.length());
        String[] parts = payload.split(":");
        if (parts.length != 2) {
            return BotResponse.of(answer(callbackQuery, "Недоступно"));
        }
        UUID locationId = parseUuid(parts[0]);
        if (locationId == null) {
            return BotResponse.of(answer(callbackQuery, "Недоступно"));
        }
        if ("BACK".equalsIgnoreCase(parts[1])) {
            BotResponse listResponse = showScheduleLocationsList(getOrCreateSession(tm.getTelegramUserId()),
                    callbackQuery.getMessage().getChatId());
            EditMessageText edit = new EditMessageText();
            edit.setChatId(callbackQuery.getMessage().getChatId().toString());
            edit.setMessageId(callbackQuery.getMessage().getMessageId());
            edit.setText("Повернення до списку локацій.");
            if (listResponse.actions().isEmpty()) {
                return BotResponse.of(edit);
            }
            return BotResponse.of(edit, listResponse.actions().get(0));
        }
        YearMonth month = parseMonth(parts[1]);
        if (month == null) {
            return BotResponse.of(answer(callbackQuery, "Недоступно"));
        }
        Optional<Location> locationOptional = locationService.findByIdAndTmUserId(locationId, tm.getTelegramUserId());
        if (locationOptional.isEmpty()) {
            return BotResponse.of(answer(callbackQuery, "Недоступно"));
        }
        TmScheduleCalendarService.TmScheduleView view = tmScheduleCalendarService.buildLocationView(
                locationOptional.get(),
                month
        );
        EditMessageText edit = new EditMessageText();
        edit.setChatId(callbackQuery.getMessage().getChatId().toString());
        edit.setMessageId(callbackQuery.getMessage().getMessageId());
        edit.setText(view.text());
        edit.setParseMode("HTML");
        edit.setReplyMarkup(view.keyboard());
        return BotResponse.of(edit);
    }

    private BotResponse handleRequestsList(TmSession session, Long chatId, String text) {
        if (BUTTON_BACK.equalsIgnoreCase(text)) {
            return showMainMenu(session.getTelegramUserId(), chatId);
        }

        List<UserAccount> requests = registrationRequestService.findPendingByTmUserId(session.getTelegramUserId());
        Optional<UserAccount> request = findRequestByButton(requests, text);
        if (request.isEmpty()) {
            SendMessage message = buildMessage(chatId, "Оберіть заявку зі списку.");
            message.setReplyMarkup(KeyboardFactory.listWithBackKeyboard(formatRequests(requests)));
            return BotResponse.of(message);
        }
        return showRequestDetails(session, chatId, request.get());
    }

    private BotResponse handleRequestDetails(TmSession session, Long chatId, String text) {
        if (BUTTON_BACK.equalsIgnoreCase(text)) {
            return showRequestsList(session, chatId);
        }
        if (BUTTON_YES.equalsIgnoreCase(text)) {
            return approveRequest(session, chatId);
        }
        if (BUTTON_REJECT.equalsIgnoreCase(text)) {
            return rejectRequest(session, chatId);
        }

        UUID requestId = session.getSelectedRequestId();
        if (requestId == null) {
            return showRequestsList(session, chatId);
        }
        Optional<UserAccount> request = registrationRequestService.findPendingByIdAndTmUserId(
                requestId,
                session.getTelegramUserId()
        );
        if (request.isEmpty()) {
            return showRequestsList(session, chatId);
        }
        return showRequestDetails(session, chatId, request.get());
    }

    private BotResponse handleLocationsMenu(TmSession session, Long chatId, String text) {
        if (BUTTON_BACK.equalsIgnoreCase(text)) {
            return showMainMenu(session.getTelegramUserId(), chatId);
        }
        if (BUTTON_ADD_LOCATION.equalsIgnoreCase(text)) {
            return showLocationAddInput(session, chatId);
        }
        if (BUTTON_DELETE_LOCATION.equalsIgnoreCase(text)) {
            return showLocationDeleteList(session, chatId);
        }
        return showLocationsMenu(session, chatId);
    }

    private BotResponse handleLocationAddInput(TmSession session, Long chatId, String text) {
        if (BUTTON_BACK.equalsIgnoreCase(text)) {
            return showLocationsMenu(session, chatId);
        }
        String normalized = normalizeName(text);
        if (normalized == null) {
            SendMessage message = buildMessage(chatId, "Назва локації не може бути порожньою.");
            message.setReplyMarkup(KeyboardFactory.backKeyboard());
            return BotResponse.of(message);
        }
        session.setDraftLocationName(normalized);
        session.setState(TmState.LOCATION_ADD_CONFIRM);
        tmSessionService.save(session);
        return showLocationAddConfirm(session, chatId);
    }

    private BotResponse handleLocationAddConfirm(TmSession session, Long chatId, String text) {
        if (BUTTON_BACK.equalsIgnoreCase(text)) {
            return showLocationsMenu(session, chatId);
        }
        if (!BUTTON_YES.equalsIgnoreCase(text)) {
            return showLocationAddConfirm(session, chatId);
        }

        String name = session.getDraftLocationName();
        if (name == null || name.isBlank()) {
            return showLocationAddInput(session, chatId);
        }
        locationService.createLocation(session.getTelegramUserId(), name);
        session.setDraftLocationName(null);
        session.setState(TmState.LOCATIONS_MENU);
        tmSessionService.save(session);

        SendMessage done = buildMessage(chatId, "Готово. Локацію додано.");
        SendMessage menu = buildLocationsMenuMessage(session, chatId);
        return BotResponse.of(done, menu);
    }

    private BotResponse handleLocationDeleteList(TmSession session, Long chatId, String text) {
        if (BUTTON_BACK.equalsIgnoreCase(text)) {
            return showLocationsMenu(session, chatId);
        }
        List<Location> locations = locationService.findActiveByTmUserId(session.getTelegramUserId());
        Optional<Location> location = findLocationByName(locations, text);
        if (location.isEmpty()) {
            SendMessage message = buildMessage(chatId, "Оберіть локацію зі списку.");
            message.setReplyMarkup(KeyboardFactory.listWithBackKeyboard(formatLocationNames(locations)));
            return BotResponse.of(message);
        }
        return showLocationDeleteConfirm(session, chatId, location.get());
    }

    private BotResponse handleLocationDeleteConfirm(TmSession session, Long chatId, String text) {
        if (BUTTON_BACK.equalsIgnoreCase(text)) {
            return showLocationDeleteList(session, chatId);
        }
        if (!BUTTON_YES.equalsIgnoreCase(text)) {
            return showLocationDeleteConfirm(session, chatId);
        }

        UUID locationId = session.getSelectedLocationId();
        if (locationId != null) {
            locationService.findByIdAndTmUserId(locationId, session.getTelegramUserId())
                    .ifPresent(locationService::deactivateLocation);
        }
        session.setSelectedLocationId(null);
        session.setState(TmState.LOCATIONS_MENU);
        tmSessionService.save(session);

        SendMessage done = buildMessage(chatId, "Готово. Локацію видалено.");
        SendMessage menu = buildLocationsMenuMessage(session, chatId);
        return BotResponse.of(done, menu);
    }

    private BotResponse showLocationDeleteConfirm(TmSession session, Long chatId) {
        UUID locationId = session.getSelectedLocationId();
        if (locationId == null) {
            return showLocationDeleteList(session, chatId);
        }
        return locationService.findByIdAndTmUserId(locationId, session.getTelegramUserId())
                .map(location -> showLocationDeleteConfirm(session, chatId, location))
                .orElseGet(() -> showLocationDeleteList(session, chatId));
    }


    private BotResponse handleScheduleLocationsList(TmSession session, Long chatId, String text) {
        if (BUTTON_BACK.equalsIgnoreCase(text)) {
            return showMainMenu(session.getTelegramUserId(), chatId);
        }
        List<Location> locations = locationService.findActiveByTmUserId(session.getTelegramUserId());
        Optional<Location> location = findLocationByName(locations, text);
        if (location.isEmpty()) {
            SendMessage message = buildMessage(chatId, "Оберіть локацію зі списку.");
            message.setReplyMarkup(KeyboardFactory.listWithBackKeyboard(formatLocationNames(locations)));
            return BotResponse.of(message);
        }
        return showScheduleLocationView(session, chatId, location.get());
    }

    private BotResponse handleScheduleLocationView(TmSession session, Long chatId, String text) {
        if (BUTTON_BACK.equalsIgnoreCase(text)) {
            return showScheduleLocationsList(session, chatId);
        }
        if (BUTTON_ENABLE_SCHEDULE.equalsIgnoreCase(text) || BUTTON_DISABLE_SCHEDULE.equalsIgnoreCase(text)) {
            return toggleScheduleEdit(session, chatId);
        }
        return showScheduleLocationView(session, chatId);
    }

    private BotResponse showRequestsList(TmSession session, Long chatId) {
        session.setState(TmState.REQUESTS_LIST);
        clearSelections(session);
        tmSessionService.save(session);

        List<UserAccount> requests = registrationRequestService.findPendingByTmUserId(session.getTelegramUserId());
        if (requests.isEmpty()) {
            SendMessage message = buildMessage(chatId, "Заявок немає.");
            message.setReplyMarkup(KeyboardFactory.backKeyboard());
            return BotResponse.of(message);
        }
        SendMessage message = buildMessage(chatId, "Оберіть заявку:");
        message.setReplyMarkup(KeyboardFactory.listWithBackKeyboard(formatRequests(requests)));
        return BotResponse.of(message);
    }

    private BotResponse showRequestDetails(TmSession session, Long chatId, UserAccount request) {
        session.setState(TmState.REQUEST_DETAILS);
        session.setSelectedRequestId(request.getId());
        tmSessionService.save(session);

        String text = """
                Додавання нового працівника
                ПІБ %s
                Локація %s
                Роль %s
                Додати нового працівника на локацію?
                """.formatted(
                request.getLastName(),
                request.getLocation() != null ? request.getLocation().getName() : "-",
                formatRole(request.getRole())
        );
        SendMessage message = buildMessage(chatId, text.trim());
        message.setReplyMarkup(KeyboardFactory.yesRejectBackKeyboard());
        return BotResponse.of(message);
    }

    private BotResponse showLocationsMenu(TmSession session, Long chatId) {
        session.setState(TmState.LOCATIONS_MENU);
        clearSelections(session);
        tmSessionService.save(session);
        return BotResponse.of(buildLocationsMenuMessage(session, chatId));
    }

    private BotResponse showLocationAddInput(TmSession session, Long chatId) {
        session.setState(TmState.LOCATION_ADD_INPUT);
        session.setDraftLocationName(null);
        tmSessionService.save(session);

        String text = """
                Додавання нової локації
                Назва: ...
                """;
        SendMessage message = buildMessage(chatId, text.trim());
        message.setReplyMarkup(KeyboardFactory.backKeyboard());
        return BotResponse.of(message);
    }

    private BotResponse showLocationAddConfirm(TmSession session, Long chatId) {
        String name = session.getDraftLocationName() == null ? "" : session.getDraftLocationName();
        String text = """
                Додавання нової локації
                Назва: %s
                Ви впевнені що хочете додати?
                """.formatted(name);
        SendMessage message = buildMessage(chatId, text.trim());
        message.setReplyMarkup(KeyboardFactory.yesBackKeyboard());
        return BotResponse.of(message);
    }

    private BotResponse showLocationDeleteList(TmSession session, Long chatId) {
        session.setState(TmState.LOCATION_DELETE_LIST);
        session.setSelectedLocationId(null);
        tmSessionService.save(session);

        List<Location> locations = locationService.findActiveByTmUserId(session.getTelegramUserId());
        if (locations.isEmpty()) {
            SendMessage message = buildMessage(chatId, "Немає локацій для видалення.");
            message.setReplyMarkup(KeyboardFactory.backKeyboard());
            return BotResponse.of(message);
        }
        SendMessage message = buildMessage(chatId, "Оберіть локацію для видалення:");
        message.setReplyMarkup(KeyboardFactory.listWithBackKeyboard(formatLocationNames(locations)));
        return BotResponse.of(message);
    }

    private BotResponse showLocationDeleteConfirm(TmSession session, Long chatId, Location location) {
        session.setState(TmState.LOCATION_DELETE_CONFIRM);
        session.setSelectedLocationId(location.getId());
        tmSessionService.save(session);

        String text = """
                Видалення локації
                Назва: %s
                Ви впевнені що хочете видалити?
                """.formatted(location.getName());
        SendMessage message = buildMessage(chatId, text.trim());
        message.setReplyMarkup(KeyboardFactory.yesBackKeyboard());
        return BotResponse.of(message);
    }



    private BotResponse showScheduleLocationsList(TmSession session, Long chatId) {
        session.setState(TmState.SCHEDULE_LOCATIONS_LIST);
        session.setSelectedLocationId(null);
        tmSessionService.save(session);

        List<Location> locations = locationService.findActiveByTmUserId(session.getTelegramUserId());
        if (locations.isEmpty()) {
            SendMessage message = buildMessage(chatId, "У вас ще немає локацій.");
            message.setReplyMarkup(KeyboardFactory.backKeyboard());
            return BotResponse.of(message);
        }
        SendMessage message = buildMessage(chatId, "Оберіть локацію:");
        message.setReplyMarkup(KeyboardFactory.listWithBackKeyboard(formatLocationNames(locations)));
        return BotResponse.of(message);
    }

    private BotResponse showScheduleLocationView(TmSession session, Long chatId, Location location) {
        session.setState(TmState.SCHEDULE_LOCATION_VIEW);
        session.setSelectedLocationId(location.getId());
        tmSessionService.save(session);

        YearMonth month = YearMonth.now();
        TmScheduleCalendarService.TmScheduleView view = tmScheduleCalendarService.buildLocationView(location, month);
        SendMessage message = buildMessage(chatId, view.text());
        message.setParseMode("HTML");
        message.setReplyMarkup(view.keyboard());

        SendMessage menu = buildMessage(chatId, "Керування внесенням графіку:");
        menu.setReplyMarkup(KeyboardFactory.tmScheduleLocationKeyboard(location.isScheduleEditEnabled()));
        return BotResponse.of(message, menu);
    }

    private BotResponse showScheduleLocationView(TmSession session, Long chatId) {
        UUID locationId = session.getSelectedLocationId();
        if (locationId == null) {
            return showScheduleLocationsList(session, chatId);
        }
        Optional<Location> location = locationService.findByIdAndTmUserId(locationId, session.getTelegramUserId());
        return location.map(loc -> showScheduleLocationView(session, chatId, loc))
                .orElseGet(() -> showScheduleLocationsList(session, chatId));
    }

    private BotResponse toggleScheduleEdit(TmSession session, Long chatId) {
        UUID locationId = session.getSelectedLocationId();
        if (locationId == null) {
            return showScheduleLocationsList(session, chatId);
        }
        Optional<Location> locationOptional = locationService.findByIdAndTmUserId(locationId, session.getTelegramUserId());
        if (locationOptional.isEmpty()) {
            return showScheduleLocationsList(session, chatId);
        }
        Location location = locationOptional.get();
        boolean newValue = !location.isScheduleEditEnabled();
        Location updated = scheduleEditGateService.updateScheduleEditFlag(location, newValue);
        UUID actorId = userAccountService.findByTelegramUserId(session.getTelegramUserId())
                .map(UserAccount::getId)
                .orElse(null);
        auditService.log(
                AuditEventType.SCHEDULE_EDIT_TOGGLED,
                actorId,
                null,
                updated.getId(),
                "ТМ змінив доступ до графіку: %s | Локація: %s".formatted(
                        newValue ? "дозволено" : "заборонено",
                        updated.getName()
                )
        );

        SendMessage notice = buildMessage(chatId,
                newValue ? "Внесення графіку дозволено." : "Внесення графіку заборонено.");
        SendMessage menu = buildMessage(chatId, "Керування внесенням графіку:");
        menu.setReplyMarkup(KeyboardFactory.tmScheduleLocationKeyboard(updated.isScheduleEditEnabled()));
        return BotResponse.of(notice, menu);
    }

    private BotResponse approveRequest(TmSession session, Long chatId) {
        UUID requestId = session.getSelectedRequestId();
        if (requestId != null) {
            registrationRequestService.findPendingByIdAndTmUserId(requestId, session.getTelegramUserId())
                    .ifPresent(request -> registrationRequestService.approve(request, session.getTelegramUserId()));
        }
        session.setSelectedRequestId(null);
        tmSessionService.save(session);

        SendMessage done = buildMessage(chatId, "Готово. Працівника додано.");
        BotResponse listResponse = showRequestsList(session, chatId);
        return BotResponse.of(done, listResponse.actions().get(0));
    }

    private BotResponse rejectRequest(TmSession session, Long chatId) {
        UUID requestId = session.getSelectedRequestId();
        if (requestId != null) {
            registrationRequestService.findPendingByIdAndTmUserId(requestId, session.getTelegramUserId())
                    .ifPresent(registrationRequestService::reject);
        }
        session.setSelectedRequestId(null);
        tmSessionService.save(session);

        SendMessage done = buildMessage(chatId, "Заявку відхилено.");
        BotResponse listResponse = showRequestsList(session, chatId);
        return BotResponse.of(done, listResponse.actions().get(0));
    }

    private List<String> formatRequests(List<UserAccount> requests) {
        return requests.stream()
                .map(request -> "%s -> %s".formatted(
                        request.getLastName(),
                        request.getLocation() != null ? request.getLocation().getName() : "-"
                ))
                .toList();
    }

    private Optional<UserAccount> findRequestByButton(List<UserAccount> requests, String buttonText) {
        return requests.stream()
                .filter(request -> ("%s -> %s".formatted(
                        request.getLastName(),
                        request.getLocation() != null ? request.getLocation().getName() : "-"
                )).equalsIgnoreCase(buttonText))
                .findFirst();
    }

    private List<String> formatLocationNames(List<Location> locations) {
        return locations.stream()
                .map(Location::getName)
                .toList();
    }

    private Optional<Location> findLocationByName(List<Location> locations, String name) {
        return locations.stream()
                .filter(location -> location.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    private SendMessage buildLocationsMenuMessage(TmSession session, Long chatId) {
        List<Location> locations = locationService.findActiveByTmUserId(session.getTelegramUserId());
        String text = locations.isEmpty()
                ? "У вас ще немає локацій."
                : buildLocationsListText(locations);
        SendMessage message = buildMessage(chatId, text);
        message.setReplyMarkup(KeyboardFactory.tmLocationsMenuKeyboard());
        return message;
    }

    private String buildLocationsListText(List<Location> locations) {
        StringBuilder builder = new StringBuilder("Ваші локації:\n");
        for (int i = 0; i < locations.size(); i++) {
            builder.append(i + 1).append(") ").append(locations.get(i).getName());
            if (i + 1 < locations.size()) {
                builder.append("\n");
            }
        }
        return builder.toString();
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

    private static String normalizeName(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private static YearMonth parseMonth(String raw) {
        try {
            return YearMonth.parse(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private static AnswerCallbackQuery answer(CallbackQuery callbackQuery, String text) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQuery.getId());
        answer.setText(text);
        return answer;
    }

    private TmSession getOrCreateSession(Long telegramUserId) {
        return tmSessionService.findByTelegramUserId(telegramUserId)
                .orElseGet(() -> TmSession.builder()
                        .telegramUserId(telegramUserId)
                        .state(TmState.MAIN_MENU)
                        .build());
    }

    private void clearSelections(TmSession session) {
        session.setSelectedLocationId(null);
        session.setSelectedRequestId(null);
        session.setDraftLocationName(null);
    }
}
