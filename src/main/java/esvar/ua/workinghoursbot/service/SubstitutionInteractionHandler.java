package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.bot.BotResponse;
import esvar.ua.workinghoursbot.bot.InlineKeyboardFactory;
import esvar.ua.workinghoursbot.domain.Location;
import esvar.ua.workinghoursbot.domain.RegistrationStatus;
import esvar.ua.workinghoursbot.domain.Role;
import esvar.ua.workinghoursbot.domain.SubstitutionCandidateState;
import esvar.ua.workinghoursbot.domain.SubstitutionRequest;
import esvar.ua.workinghoursbot.domain.SubstitutionRequestCandidate;
import esvar.ua.workinghoursbot.domain.SubstitutionRequestScope;
import esvar.ua.workinghoursbot.domain.UserAccount;
import esvar.ua.workinghoursbot.domain.SubstitutionRequestStatus;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubstitutionInteractionHandler {

    private static final String COMMAND_SUBSTITUTION = "🔁 Підміна";
    private static final String COMMAND_ACTIVE_REQUESTS = "📌 Активні запити на підміни";

    private static final String CB_SUB_CREATE = "SUB_REQ_CREATE:";
    private static final String CB_SUB_CREATE_URGENT = "SUB_REQ_CREATE_URGENT_TODAY";
    private static final String CB_SUB_CONFIRM = "SUB_REQ_CONFIRM:";
    private static final String CB_SUB_CANCEL = "SUB_REQ_CANCEL_CREATE";
    private static final String CB_SUB_PAGE = "SUB_REQ_PAGE:";

    private static final String CB_SENIOR_OPEN = "SENIOR_SUB_OPEN:";
    private static final String CB_SENIOR_TAKE = "SENIOR_SUB_TAKE:";
    private static final String CB_SENIOR_FIND = "SENIOR_SUB_FIND:";
    private static final String CB_SENIOR_SCOPE = "SENIOR_SUB_SCOPE:";
    private static final String CB_SENIOR_NOTIFY_ALL = "SENIOR_SUB_NOTIFY_ALL:";
    private static final String CB_SENIOR_PICK_LIST = "SENIOR_SUB_PICK_LIST:";
    private static final String CB_SENIOR_PICK_PAGE = "SENIOR_SUB_PICK_PAGE:";
    private static final String CB_SENIOR_PICK = "SENIOR_SUB_PICK:";
    private static final String CB_SENIOR_REJECT = "SENIOR_SUB_REJECT:";
    private static final String CB_SENIOR_REJECT_REASON = "SENIOR_SUB_REJECT_REASON:";
    private static final String CB_SENIOR_ACTIVE_LIST = "SENIOR_SUB_ACTIVE_LIST";
    private static final String CB_SENIOR_ACTIVE_OPEN = "SENIOR_SUB_ACTIVE_OPEN:";
    private static final String CB_SENIOR_ACTIVE_PAGE = "SENIOR_SUB_ACTIVE_PAGE:";
    private static final String CB_SENIOR_TM_REJECT_MENU = "SENIOR_SUB_TM_REJECT_MENU:";
    private static final String CB_SENIOR_STAY_WORKING = "SENIOR_SUB_STAY_WORKING:";
    private static final String CB_SENIOR_FIND_AGAIN = "SENIOR_SUB_FIND_AGAIN:";

    private static final String CB_CANDIDATE_ACCEPT = "CAND_SUB_ACCEPT:";
    private static final String CB_CANDIDATE_DECLINE = "CAND_SUB_DECLINE:";

    private static final String CB_TM_APPROVE = "TM_SUB_APPROVE:";
    private static final String CB_TM_REJECT = "TM_SUB_REJECT:";

    private static final String CB_NAV_BACK = "NAV_BACK:";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_SHORT_FORMAT = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private static final int DATE_PAGE_SIZE = 9;
    private static final int CANDIDATE_PAGE_SIZE = 8;
    private static final int ACTIVE_REQUEST_PAGE_SIZE = 8;

    private final SubstitutionService substitutionService;
    private final SubstitutionDraftStore substitutionDraftStore;
    private final SubstitutionMenuSessionStore substitutionMenuSessionStore;
    private final TelegramNotificationService notificationService;
    private final UserAccountService userAccountService;
    private final SubstitutionNotificationService substitutionNotificationService;

    public BotResponse handleMessage(Long telegramUserId, Long chatId, String text) {
        if (COMMAND_ACTIVE_REQUESTS.equalsIgnoreCase(text)) {
            Optional<UserAccount> accountOptional = userAccountService.findByTelegramUserId(telegramUserId);
            if (accountOptional.isEmpty()) {
                return BotResponse.empty();
            }
            UserAccount account = accountOptional.get();
            if (account.getStatus() != RegistrationStatus.APPROVED || account.getRole() != Role.SENIOR_SELLER) {
                return BotResponse.empty();
            }
            ActiveRequestsView view = buildActiveRequestsView(account, 0);
            return BotResponse.of(notificationService.sendMessage(chatId, view.text(), view.keyboard()));
        }

        if (!COMMAND_SUBSTITUTION.equalsIgnoreCase(text)) {
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
        DateSelectionView view = buildDateSelection(telegramUserId, 0);
        return BotResponse.of(renderSellerMenu(telegramUserId, chatId, view));
    }

    public BotResponse handleCallback(CallbackQuery callbackQuery) {
        if (callbackQuery == null || callbackQuery.getFrom() == null) {
            return BotResponse.empty();
        }
        String data = callbackQuery.getData();
        if (data == null || data.isBlank()) {
            return BotResponse.empty();
        }

        try {
            BotResponse response = null;
            if (data.startsWith(CB_SUB_CREATE)) {
                response = handleCreateSelection(callbackQuery);
            } else if (CB_SUB_CREATE_URGENT.equals(data)) {
                response = handleUrgentSelection(callbackQuery);
            } else if (data.startsWith(CB_SUB_CONFIRM)) {
                response = handleConfirm(callbackQuery);
            } else if (CB_SUB_CANCEL.equals(data)) {
                response = handleCancelDraft(callbackQuery);
            } else if (data.startsWith(CB_SUB_PAGE)) {
                response = handleDatePage(callbackQuery);
            } else if (data.startsWith(CB_SENIOR_OPEN)) {
                response = handleSeniorOpen(callbackQuery);
            } else if (data.startsWith(CB_SENIOR_TAKE)) {
                response = handleSeniorTake(callbackQuery);
            } else if (data.startsWith(CB_SENIOR_FIND)) {
                response = handleSeniorFind(callbackQuery);
            } else if (data.startsWith(CB_SENIOR_SCOPE)) {
                response = handleSeniorScope(callbackQuery);
            } else if (data.startsWith(CB_SENIOR_NOTIFY_ALL)) {
                response = handleNotifyAll(callbackQuery);
            } else if (data.startsWith(CB_SENIOR_PICK_LIST)) {
                response = handlePickList(callbackQuery);
            } else if (data.startsWith(CB_SENIOR_PICK_PAGE)) {
                response = handlePickPage(callbackQuery);
            } else if (data.startsWith(CB_SENIOR_PICK)) {
                response = handlePickCandidate(callbackQuery);
            } else if (data.startsWith(CB_SENIOR_REJECT)) {
                response = handleSeniorReject(callbackQuery);
            } else if (data.startsWith(CB_SENIOR_REJECT_REASON)) {
                response = handleRejectReason(callbackQuery);
            } else if (CB_SENIOR_ACTIVE_LIST.equals(data)) {
                response = handleSeniorActiveList(callbackQuery);
            } else if (data.startsWith(CB_SENIOR_ACTIVE_OPEN)) {
                response = handleSeniorActiveOpen(callbackQuery);
            } else if (data.startsWith(CB_SENIOR_ACTIVE_PAGE)) {
                response = handleSeniorActivePage(callbackQuery);
            } else if (data.startsWith(CB_SENIOR_TM_REJECT_MENU)) {
                response = handleSeniorTmRejectMenu(callbackQuery);
            } else if (data.startsWith(CB_SENIOR_STAY_WORKING)) {
                response = handleSeniorStayWorking(callbackQuery);
            } else if (data.startsWith(CB_SENIOR_FIND_AGAIN)) {
                response = handleSeniorFindAgain(callbackQuery);
            } else if (data.startsWith(CB_CANDIDATE_ACCEPT)) {
                response = handleCandidateAccept(callbackQuery);
            } else if (data.startsWith(CB_CANDIDATE_DECLINE)) {
                response = handleCandidateDecline(callbackQuery);
            } else if (data.startsWith(CB_TM_APPROVE)) {
                response = handleTmApprove(callbackQuery);
            } else if (data.startsWith(CB_TM_REJECT)) {
                response = handleTmReject(callbackQuery);
            } else if (data.startsWith(CB_NAV_BACK)) {
                response = handleNavBack(callbackQuery);
            }
            if (response == null) {
                return BotResponse.empty();
            }
            return withAnswer(callbackQuery, response);
        } catch (IllegalStateException ex) {
            log.warn("Substitution callback validation failed: data={}, user={}",
                    data,
                    callbackQuery.getFrom().getId(),
                    ex);
            return BotResponse.of(notificationService.answerCallbackQuery(callbackQuery.getId(), ex.getMessage()));
        } catch (Exception ex) {
            log.error("Substitution callback failed: data={}, user={}",
                    data,
                    callbackQuery.getFrom().getId(),
                    ex);
            return BotResponse.of(notificationService.answerCallbackQuery(
                    callbackQuery.getId(),
                    "Сталася помилка. Спробуйте ще раз."
            ));
        }
    }

    private DateSelectionView buildDateSelection(Long telegramUserId, int page) {
        List<LocalDate> dates = substitutionService.getPlannedWorkDates(telegramUserId);
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (dates.isEmpty()) {
            rows.add(List.of(InlineKeyboardFactory.button("⬅️ Назад", CB_NAV_BACK + "SUB_EXIT")));
            return new DateSelectionView("Немає запланованих робочих днів для підміни.", InlineKeyboardFactory.rows(rows));
        }

        LocalDate today = LocalDate.now();
        boolean urgentToday = dates.contains(today);
        List<LocalDate> selectableDates = new ArrayList<>(dates);
        selectableDates.removeIf(date -> urgentToday && date.equals(today));

        int totalPages = Math.max(1, (int) Math.ceil(selectableDates.size() / (double) DATE_PAGE_SIZE));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int start = safePage * DATE_PAGE_SIZE;
        int end = Math.min(selectableDates.size(), start + DATE_PAGE_SIZE);

        if (urgentToday && safePage == 0) {
            rows.add(List.of(InlineKeyboardFactory.button(
                    "🔥 ТЕРМІНОВО: сьогодні (" + DATE_SHORT_FORMAT.format(today) + ")",
                    CB_SUB_CREATE_URGENT
            )));
        }

        List<InlineKeyboardButton> currentRow = new ArrayList<>();
        for (LocalDate date : selectableDates.subList(start, end)) {
            currentRow.add(InlineKeyboardFactory.button(DATE_SHORT_FORMAT.format(date), CB_SUB_CREATE + date));
            if (currentRow.size() == 3) {
                rows.add(currentRow);
                currentRow = new ArrayList<>();
            }
        }
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }

        if (totalPages > 1) {
            List<InlineKeyboardButton> navRow = new ArrayList<>();
            if (safePage > 0) {
                navRow.add(InlineKeyboardFactory.button("◀️ Попередня", CB_SUB_PAGE + (safePage - 1)));
            }
            if (safePage < totalPages - 1) {
                navRow.add(InlineKeyboardFactory.button("▶️ Далі", CB_SUB_PAGE + (safePage + 1)));
            }
            rows.add(navRow);
        }
        rows.add(List.of(InlineKeyboardFactory.button("⬅️ Назад", CB_NAV_BACK + "SUB_EXIT")));
        InlineKeyboardMarkup keyboard = InlineKeyboardFactory.rows(rows);
        return new DateSelectionView("Оберіть дату для підміни:", keyboard);
    }

    private BotResponse handleCreateSelection(CallbackQuery callbackQuery) {
        Long telegramUserId = callbackQuery.getFrom().getId();
        LocalDate date = LocalDate.parse(callbackQuery.getData().substring(CB_SUB_CREATE.length()));
        return BotResponse.of(renderConfirmation(callbackQuery, telegramUserId, date, false));
    }

    private BotResponse handleUrgentSelection(CallbackQuery callbackQuery) {
        Long telegramUserId = callbackQuery.getFrom().getId();
        LocalDate today = LocalDate.now();
        return BotResponse.of(renderConfirmation(callbackQuery, telegramUserId, today, true));
    }

    private BotApiMethod<?> renderConfirmation(CallbackQuery callbackQuery,
                                               Long telegramUserId,
                                               LocalDate date,
                                               boolean urgent) {
        UserAccount account = userAccountService.findByTelegramUserId(telegramUserId)
                .orElseThrow(() -> new IllegalStateException("Користувача не знайдено."));
        Location location = account.getLocation();
        if (location == null) {
            return notificationService.answerCallbackQuery(callbackQuery.getId(), "Спочатку оберіть локацію.");
        }
        SubstitutionDraftStore.Draft draft = substitutionDraftStore.createDraft(telegramUserId, date, urgent);
        String text = """
                ⚠️ Ви створюєте запит на підміну
                📍 Локація: %s
                📅 Дата: %s
                Підтвердити?
                """.formatted(location.getName(), DATE_FORMAT.format(date));
        InlineKeyboardMarkup keyboard = InlineKeyboardFactory.rows(List.of(
                List.of(
                        InlineKeyboardFactory.button("✅ Підтвердити", CB_SUB_CONFIRM + CallbackIdEncoder.encode(draft.getId())),
                        InlineKeyboardFactory.button("❌ Скасувати", CB_SUB_CANCEL)
                )
        ));
        return editSellerMenu(telegramUserId, callbackQuery.getMessage(), text, keyboard);
    }

    private BotResponse handleConfirm(CallbackQuery callbackQuery) {
        Long telegramUserId = callbackQuery.getFrom().getId();
        UUID draftId = CallbackIdEncoder.decode(callbackQuery.getData().substring(CB_SUB_CONFIRM.length()));
        Optional<SubstitutionDraftStore.Draft> draftOptional = substitutionDraftStore.findDraft(telegramUserId, draftId);
        if (draftOptional.isEmpty()) {
            return BotResponse.of(notificationService.answerCallbackQuery(
                    callbackQuery.getId(),
                    "Сесію завершено, почніть знову."
            ));
        }
        SubstitutionDraftStore.Draft draft = draftOptional.get();
        SubstitutionRequest request = substitutionService.createRequest(
                telegramUserId,
                draft.getDate(),
                draft.isUrgent(),
                draft.getId(),
                "не можу"
        );
        substitutionDraftStore.clearDraft(telegramUserId);

        List<BotApiMethod<?>> actions = new ArrayList<>();
        InlineKeyboardMarkup keyboard = InlineKeyboardFactory.rows(List.of(
                List.of(InlineKeyboardFactory.button("⬅️ Назад в меню", CB_NAV_BACK + "SUB_CREATE"))
        ));
        actions.add(editSellerMenu(
                telegramUserId,
                callbackQuery.getMessage(),
                "✅ Запит на підміну створено. Очікуйте рішення старшого продавця.",
                keyboard
        ));
        actions.addAll(substitutionNotificationService.notifySeniorAboutRequest(request));
        return new BotResponse(actions);
    }

    private BotResponse handleCancelDraft(CallbackQuery callbackQuery) {
        substitutionDraftStore.clearDraft(callbackQuery.getFrom().getId());
        DateSelectionView view = buildDateSelection(callbackQuery.getFrom().getId(), 0);
        return BotResponse.of(editSellerMenu(callbackQuery.getFrom().getId(), callbackQuery.getMessage(), view.text(), view.keyboard()));
    }

    private BotResponse handleDatePage(CallbackQuery callbackQuery) {
        Long telegramUserId = callbackQuery.getFrom().getId();
        int page = Integer.parseInt(callbackQuery.getData().substring(CB_SUB_PAGE.length()));
        DateSelectionView view = buildDateSelection(telegramUserId, page);
        return BotResponse.of(editSellerMenu(telegramUserId, callbackQuery.getMessage(), view.text(), view.keyboard()));
    }

    private BotResponse handleSeniorOpen(CallbackQuery callbackQuery) {
        requireSenior(callbackQuery);
        UUID requestId = CallbackIdEncoder.decode(callbackQuery.getData().substring(CB_SENIOR_OPEN.length()));
        SubstitutionRequest request = substitutionService.getRequest(requestId);
        return BotResponse.of(renderSeniorRequestMenu(callbackQuery.getMessage(), request));
    }

    private BotResponse handleSeniorTake(CallbackQuery callbackQuery) {
        UUID requestId = CallbackIdEncoder.decode(callbackQuery.getData().substring(CB_SENIOR_TAKE.length()));
        UserAccount senior = requireSenior(callbackQuery);
        SubstitutionRequest request = substitutionService.approveBySenior(requestId, senior.getTelegramUserId());
        SubstitutionRequest saved = substitutionService.submitToTmApproval(request.getId(), senior.getId());

        List<BotApiMethod<?>> actions = new ArrayList<>();
        actions.add(editMessage(callbackQuery.getMessage(), "✅ Запит відправлено на підтвердження ТМ.", null));
        actions.addAll(notifyTmApproval(saved));
        return new BotResponse(actions);
    }

    private BotResponse handleSeniorFind(CallbackQuery callbackQuery) {
        requireSenior(callbackQuery);
        UUID requestId = CallbackIdEncoder.decode(callbackQuery.getData().substring(CB_SENIOR_FIND.length()));
        return BotResponse.of(renderScopeMenu(callbackQuery.getMessage(), requestId));
    }

    private BotResponse handleSeniorScope(CallbackQuery callbackQuery) {
        String[] parts = callbackQuery.getData().substring(CB_SENIOR_SCOPE.length()).split(":");
        if (parts.length != 2) {
            return BotResponse.empty();
        }
        UserAccount senior = requireSenior(callbackQuery);
        UUID requestId = CallbackIdEncoder.decode(parts[0]);
        SubstitutionRequestScope scope = SubstitutionRequestScope.valueOf(parts[1]);
        SubstitutionRequest request = substitutionService.setScope(requestId, scope);
        List<UserAccount> candidates = substitutionService.findCandidates(requestId, scope, senior);
        String text = renderCandidatesSummary(request, candidates);
        InlineKeyboardMarkup keyboard = InlineKeyboardFactory.rows(List.of(
                List.of(
                        InlineKeyboardFactory.button("📣 Надіслати всім", CB_SENIOR_NOTIFY_ALL + CallbackIdEncoder.encode(requestId)),
                        InlineKeyboardFactory.button("👤 Обрати зі списку", CB_SENIOR_PICK_LIST + CallbackIdEncoder.encode(requestId))
                ),
                List.of(InlineKeyboardFactory.button("⬅️ Назад", CB_NAV_BACK + "SENIOR_SCOPE:" + CallbackIdEncoder.encode(requestId)))
        ));
        return BotResponse.of(editMessage(callbackQuery.getMessage(), text, keyboard));
    }

    private BotResponse handleNotifyAll(CallbackQuery callbackQuery) {
        UUID requestId = CallbackIdEncoder.decode(callbackQuery.getData().substring(CB_SENIOR_NOTIFY_ALL.length()));
        UserAccount senior = requireSenior(callbackQuery);
        SubstitutionRequest request = substitutionService.getRequest(requestId);
        List<UserAccount> candidates = substitutionService.notifyAllCandidates(requestId, request.getScope(), senior);
        List<BotApiMethod<?>> actions = new ArrayList<>();
        actions.add(editMessage(callbackQuery.getMessage(), "📣 Пропозицію надіслано кандидатам.", null));
        actions.addAll(buildCandidateOffers(request, candidates));
        return new BotResponse(actions);
    }

    private BotResponse handlePickList(CallbackQuery callbackQuery) {
        UUID requestId = CallbackIdEncoder.decode(callbackQuery.getData().substring(CB_SENIOR_PICK_LIST.length()));
        UserAccount senior = requireSenior(callbackQuery);
        SubstitutionRequest request = substitutionService.getRequest(requestId);
        List<UserAccount> candidates = substitutionService.findCandidates(requestId, request.getScope(), senior);
        InlineKeyboardMarkup keyboard = buildCandidatePickKeyboard(requestId, candidates, 0);
        String text = renderCandidatesSummary(request, candidates);
        return BotResponse.of(editMessage(callbackQuery.getMessage(), text, keyboard));
    }

    private BotResponse handlePickPage(CallbackQuery callbackQuery) {
        String[] parts = callbackQuery.getData().substring(CB_SENIOR_PICK_PAGE.length()).split(":");
        if (parts.length != 2) {
            return BotResponse.empty();
        }
        UUID requestId = CallbackIdEncoder.decode(parts[0]);
        int page = Integer.parseInt(parts[1]);
        UserAccount senior = requireSenior(callbackQuery);
        SubstitutionRequest request = substitutionService.getRequest(requestId);
        List<UserAccount> candidates = substitutionService.findCandidates(requestId, request.getScope(), senior);
        InlineKeyboardMarkup keyboard = buildCandidatePickKeyboard(requestId, candidates, page);
        String text = renderCandidatesSummary(request, candidates);
        return BotResponse.of(editMessage(callbackQuery.getMessage(), text, keyboard));
    }

    private BotResponse handlePickCandidate(CallbackQuery callbackQuery) {
        String[] parts = callbackQuery.getData().substring(CB_SENIOR_PICK.length()).split(":");
        if (parts.length != 2) {
            return BotResponse.empty();
        }
        UserAccount senior = requireSenior(callbackQuery);
        UUID requestId = CallbackIdEncoder.decode(parts[0]);
        UUID candidateId = CallbackIdEncoder.decode(parts[1]);
        SubstitutionRequest request = substitutionService.getRequest(requestId);
        UserAccount candidate = substitutionService.notifySingleCandidate(requestId, candidateId, request.getScope(), senior);

        List<BotApiMethod<?>> actions = new ArrayList<>();
        actions.add(editMessage(callbackQuery.getMessage(), "✅ Пропозицію надіслано кандидату.", null));
        actions.addAll(buildCandidateOffers(request, List.of(candidate)));
        return new BotResponse(actions);
    }

    private BotResponse handleSeniorReject(CallbackQuery callbackQuery) {
        requireSenior(callbackQuery);
        UUID requestId = CallbackIdEncoder.decode(callbackQuery.getData().substring(CB_SENIOR_REJECT.length()));
        InlineKeyboardMarkup keyboard = InlineKeyboardFactory.rows(List.of(
                List.of(
                        InlineKeyboardFactory.button("❌ Немає заміни", CB_SENIOR_REJECT_REASON + CallbackIdEncoder.encode(requestId) + ":NO_REPL"),
                        InlineKeyboardFactory.button("❌ Запізно створений запит", CB_SENIOR_REJECT_REASON + CallbackIdEncoder.encode(requestId) + ":TOO_LATE")
                ),
                List.of(
                        InlineKeyboardFactory.button("❌ Інша причина", CB_SENIOR_REJECT_REASON + CallbackIdEncoder.encode(requestId) + ":OTHER")
                ),
                List.of(InlineKeyboardFactory.button("⬅️ Назад", CB_NAV_BACK + "SENIOR_SCOPE:" + CallbackIdEncoder.encode(requestId)))
        ));
        return BotResponse.of(editMessage(callbackQuery.getMessage(), "Оберіть причину відхилення:", keyboard));
    }

    private BotResponse handleRejectReason(CallbackQuery callbackQuery) {
        String[] parts = callbackQuery.getData().substring(CB_SENIOR_REJECT_REASON.length()).split(":");
        if (parts.length != 2) {
            return BotResponse.empty();
        }
        UserAccount senior = requireSenior(callbackQuery);
        UUID requestId = CallbackIdEncoder.decode(parts[0]);
        String reasonKey = parts[1];
        String reason = switch (reasonKey) {
            case "NO_REPL" -> "Немає заміни";
            case "TOO_LATE" -> "Запізно створений запит";
            default -> "Інша причина";
        };
        SubstitutionRequest request = substitutionService.rejectRequest(requestId, senior.getTelegramUserId(), reason);
        List<BotApiMethod<?>> actions = new ArrayList<>();
        actions.add(editMessage(callbackQuery.getMessage(), "❌ Запит відхилено. Причина: " + reason, null));
        actions.add(notificationService.sendMessage(
                request.getRequester().getTelegramChatId(),
                "❌ Ваш запит на підміну відхилено. Причина: " + reason,
                null
        ));
        return new BotResponse(actions);
    }

    private BotResponse handleSeniorActiveList(CallbackQuery callbackQuery) {
        UserAccount senior = requireSenior(callbackQuery);
        ActiveRequestsView view = buildActiveRequestsView(senior, 0);
        return BotResponse.of(editMessage(callbackQuery.getMessage(), view.text(), view.keyboard()));
    }

    private BotResponse handleSeniorActiveOpen(CallbackQuery callbackQuery) {
        requireSenior(callbackQuery);
        UUID requestId = CallbackIdEncoder.decode(callbackQuery.getData().substring(CB_SENIOR_ACTIVE_OPEN.length()));
        SubstitutionRequest request = substitutionService.getRequest(requestId);
        return BotResponse.of(renderSeniorRequestMenu(callbackQuery.getMessage(), request));
    }

    private BotResponse handleSeniorActivePage(CallbackQuery callbackQuery) {
        UserAccount senior = requireSenior(callbackQuery);
        int page = Integer.parseInt(callbackQuery.getData().substring(CB_SENIOR_ACTIVE_PAGE.length()));
        ActiveRequestsView view = buildActiveRequestsView(senior, page);
        return BotResponse.of(editMessage(callbackQuery.getMessage(), view.text(), view.keyboard()));
    }

    private BotResponse handleSeniorTmRejectMenu(CallbackQuery callbackQuery) {
        requireSenior(callbackQuery);
        UUID requestId = CallbackIdEncoder.decode(callbackQuery.getData().substring(CB_SENIOR_TM_REJECT_MENU.length()));
        return BotResponse.of(editMessage(
                callbackQuery.getMessage(),
                buildTmRejectMenuText(substitutionService.getRequest(requestId)),
                buildTmRejectMenuKeyboard(requestId)
        ));
    }

    private BotResponse handleSeniorStayWorking(CallbackQuery callbackQuery) {
        UUID requestId = CallbackIdEncoder.decode(callbackQuery.getData().substring(CB_SENIOR_STAY_WORKING.length()));
        UserAccount senior = requireSenior(callbackQuery);
        SubstitutionRequest request = substitutionService.cancelByStayWorking(requestId, senior.getTelegramUserId());
        List<BotApiMethod<?>> actions = new ArrayList<>();
        actions.add(editMessage(callbackQuery.getMessage(), "❌ Підміну скасовано.", null));
        actions.addAll(notifyCancellation(request));
        return new BotResponse(actions);
    }

    private BotResponse handleSeniorFindAgain(CallbackQuery callbackQuery) {
        UUID requestId = CallbackIdEncoder.decode(callbackQuery.getData().substring(CB_SENIOR_FIND_AGAIN.length()));
        requireSenior(callbackQuery);
        return BotResponse.of(renderScopeMenu(callbackQuery.getMessage(), requestId));
    }

    private BotResponse handleTmApprove(CallbackQuery callbackQuery) {
        UserAccount tm = requireTm(callbackQuery);
        UUID requestId = CallbackIdEncoder.decode(callbackQuery.getData().substring(CB_TM_APPROVE.length()));
        SubstitutionRequest request = substitutionService.tmApprove(requestId, tm.getTelegramUserId());

        List<BotApiMethod<?>> actions = new ArrayList<>();
        actions.add(editMessage(callbackQuery.getMessage(), "✅ Підміну підтверджено. Запит закрито.", null));
        actions.addAll(notifyRequesterAndSeniors(request));
        actions.addAll(notifyOtherCandidates(request));
        return new BotResponse(actions);
    }

    private BotResponse handleTmReject(CallbackQuery callbackQuery) {
        UserAccount tm = requireTm(callbackQuery);
        UUID requestId = CallbackIdEncoder.decode(callbackQuery.getData().substring(CB_TM_REJECT.length()));
        SubstitutionRequest beforeDecision = substitutionService.getRequest(requestId);
        SubstitutionRequest request = substitutionService.tmReject(requestId, tm.getTelegramUserId());
        request.setProposedReplacementUser(beforeDecision.getProposedReplacementUser());

        List<BotApiMethod<?>> actions = new ArrayList<>();
        actions.add(editMessage(callbackQuery.getMessage(), "❌ Підміну відхилено.", null));
        actions.addAll(notifyTmRejection(request));
        return new BotResponse(actions);
    }

    private BotResponse handleCandidateAccept(CallbackQuery callbackQuery) {
        UUID requestId = CallbackIdEncoder.decode(callbackQuery.getData().substring(CB_CANDIDATE_ACCEPT.length()));
        SubstitutionService.AcceptOfferResult result = substitutionService.acceptOffer(
                requestId,
                callbackQuery.getFrom().getId()
        );

        if (result.getStatus() != SubstitutionService.AcceptOfferResult.Status.WAITING_TM_APPROVAL) {
            List<BotApiMethod<?>> actions = new ArrayList<>();
            actions.add(notificationService.answerCallbackQuery(callbackQuery.getId(), "Підміну вже взяли."));
            actions.add(editMessage(
                    callbackQuery.getMessage(),
                    "✅ Підміну вже взяли, дякуємо.",
                    null
            ));
            return new BotResponse(actions);
        }

        SubstitutionRequest request = result.getRequest();

        List<BotApiMethod<?>> actions = new ArrayList<>();
        actions.add(editMessage(
                callbackQuery.getMessage(),
                "✅ Дякуємо! Очікуємо підтвердження ТМ.",
                null
        ));
        actions.addAll(notifyTmApproval(substitutionService.submitToTmApproval(request.getId(), result.getCandidate().getId())));
        return new BotResponse(actions);
    }

    private BotResponse handleCandidateDecline(CallbackQuery callbackQuery) {
        UUID requestId = CallbackIdEncoder.decode(callbackQuery.getData().substring(CB_CANDIDATE_DECLINE.length()));
        substitutionService.declineOffer(requestId, callbackQuery.getFrom().getId());
        List<BotApiMethod<?>> actions = new ArrayList<>();
        actions.add(notificationService.answerCallbackQuery(callbackQuery.getId(), "Дякуємо за відповідь."));
        actions.add(editMessage(callbackQuery.getMessage(), "Ви відмовилися від підміни.", null));
        return new BotResponse(actions);
    }

    private BotResponse handleNavBack(CallbackQuery callbackQuery) {
        String context = callbackQuery.getData().substring(CB_NAV_BACK.length());
        if (context.startsWith("SUB_CREATE")) {
            DateSelectionView view = buildDateSelection(callbackQuery.getFrom().getId(), 0);
            return BotResponse.of(editSellerMenu(callbackQuery.getFrom().getId(), callbackQuery.getMessage(), view.text(), view.keyboard()));
        }
        if (context.startsWith("SUB_EXIT")) {
            return BotResponse.of(editSellerMenu(
                    callbackQuery.getFrom().getId(),
                    callbackQuery.getMessage(),
                    "Меню підміни закрито.",
                    null
            ));
        }
        if (context.startsWith("SENIOR_ACTIVE_EXIT")) {
            return BotResponse.of(editMessage(callbackQuery.getMessage(), "Меню підміни закрито.", null));
        }
        if (context.startsWith("SENIOR_SCOPE:")) {
            UUID requestId = CallbackIdEncoder.decode(context.substring("SENIOR_SCOPE:".length()));
            SubstitutionRequest request = substitutionService.getRequest(requestId);
            return BotResponse.of(renderSeniorRequestMenu(callbackQuery.getMessage(), request));
        }
        if (context.startsWith("SENIOR_PICK:")) {
            UUID requestId = CallbackIdEncoder.decode(context.substring("SENIOR_PICK:".length()));
            return BotResponse.of(renderScopeMenu(callbackQuery.getMessage(), requestId));
        }
        return BotResponse.empty();
    }

    private EditMessageText renderSeniorRequestMenu(Message message, SubstitutionRequest request) {
        String statusLine = switch (request.getStatus()) {
            case WAITING_TM_APPROVAL -> "⏳ Очікує підтвердження ТМ";
            case APPROVED -> "✅ Підтверджено";
            case REJECTED -> "❌ Відхилено";
            case CANCELLED -> "❌ Скасовано";
            default -> "🕒 В роботі";
        };
        String text = """
                🔁 Запит на підміну
                👤 Продавець: %s
                📍 Локація: %s
                📅 Дата: %s
                🕒 Створено: %s
                %s
                """.formatted(
                request.getRequester().getLastName(),
                request.getLocation().getName(),
                DATE_FORMAT.format(request.getRequestDate()),
                DATE_TIME_FORMAT.format(request.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime()),
                statusLine
        );
        InlineKeyboardMarkup keyboard = substitutionNotificationService.buildSeniorInlineKeyboard(request);
        return editMessage(message, text, keyboard);
    }

    private EditMessageText renderScopeMenu(Message message, UUID requestId) {
        InlineKeyboardMarkup keyboard = InlineKeyboardFactory.rows(List.of(
                List.of(
                        InlineKeyboardFactory.button("📍 По локації", CB_SENIOR_SCOPE + CallbackIdEncoder.encode(requestId) + ":LOCATION"),
                        InlineKeyboardFactory.button("👔 По ТМ", CB_SENIOR_SCOPE + CallbackIdEncoder.encode(requestId) + ":TM")
                ),
                List.of(InlineKeyboardFactory.button("🌍 Всі", CB_SENIOR_SCOPE + CallbackIdEncoder.encode(requestId) + ":ALL")),
                List.of(InlineKeyboardFactory.button("⬅️ Назад", CB_NAV_BACK + "SENIOR_SCOPE:" + CallbackIdEncoder.encode(requestId)))
        ));
        return editMessage(message, "Де шукати заміну?", keyboard);
    }

    private ActiveRequestsView buildActiveRequestsView(UserAccount senior, int page) {
        List<SubstitutionRequest> requests = substitutionService.listActiveRequestsForSenior(senior);
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        String text = "📌 Активні запити на підміну: " + requests.size();
        if (requests.isEmpty()) {
            rows.add(List.of(InlineKeyboardFactory.button("⬅️ Назад", CB_NAV_BACK + "SENIOR_ACTIVE_EXIT")));
            return new ActiveRequestsView(text, InlineKeyboardFactory.rows(rows));
        }

        int totalPages = Math.max(1, (int) Math.ceil(requests.size() / (double) ACTIVE_REQUEST_PAGE_SIZE));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int start = safePage * ACTIVE_REQUEST_PAGE_SIZE;
        int end = Math.min(requests.size(), start + ACTIVE_REQUEST_PAGE_SIZE);

        for (SubstitutionRequest request : requests.subList(start, end)) {
            String label = "👤 " + request.getRequester().getLastName()
                    + " • " + DATE_SHORT_FORMAT.format(request.getRequestDate())
                    + " • " + request.getLocation().getName();
            rows.add(List.of(InlineKeyboardFactory.button(
                    label,
                    CB_SENIOR_ACTIVE_OPEN + CallbackIdEncoder.encode(request.getId())
            )));
        }

        if (totalPages > 1) {
            List<InlineKeyboardButton> navRow = new ArrayList<>();
            if (safePage > 0) {
                navRow.add(InlineKeyboardFactory.button("◀️ Попередня", CB_SENIOR_ACTIVE_PAGE + (safePage - 1)));
            }
            if (safePage < totalPages - 1) {
                navRow.add(InlineKeyboardFactory.button("▶️ Далі", CB_SENIOR_ACTIVE_PAGE + (safePage + 1)));
            }
            rows.add(navRow);
        }

        rows.add(List.of(InlineKeyboardFactory.button("⬅️ Назад", CB_NAV_BACK + "SENIOR_ACTIVE_EXIT")));
        return new ActiveRequestsView(text, InlineKeyboardFactory.rows(rows));
    }

    private String buildTmRejectMenuText(SubstitutionRequest request) {
        return "❌ ТМ відхилив підміну на " + DATE_FORMAT.format(request.getRequestDate()) + ".\nЩо робимо далі?";
    }

    private InlineKeyboardMarkup buildTmRejectMenuKeyboard(UUID requestId) {
        return InlineKeyboardFactory.rows(List.of(
                List.of(
                        InlineKeyboardFactory.button("🧍‍♂️ Сиди працюй", CB_SENIOR_STAY_WORKING + CallbackIdEncoder.encode(requestId)),
                        InlineKeyboardFactory.button("🔄 Інший кандидат", CB_SENIOR_FIND_AGAIN + CallbackIdEncoder.encode(requestId))
                )
        ));
    }

    private InlineKeyboardMarkup buildCandidatePickKeyboard(UUID requestId, List<UserAccount> candidates, int page) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (!candidates.isEmpty()) {
            int totalPages = Math.max(1, (int) Math.ceil(candidates.size() / (double) CANDIDATE_PAGE_SIZE));
            int safePage = Math.max(0, Math.min(page, totalPages - 1));
            int start = safePage * CANDIDATE_PAGE_SIZE;
            int end = Math.min(candidates.size(), start + CANDIDATE_PAGE_SIZE);
            List<InlineKeyboardButton> row = new ArrayList<>();
            for (UserAccount candidate : candidates.subList(start, end)) {
                row.add(InlineKeyboardFactory.button(
                        candidate.getLastName(),
                        CB_SENIOR_PICK + CallbackIdEncoder.encode(requestId) + ":" + CallbackIdEncoder.encode(candidate.getId())
                ));
                if (row.size() == 2) {
                    rows.add(row);
                    row = new ArrayList<>();
                }
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
            if (totalPages > 1) {
                List<InlineKeyboardButton> navRow = new ArrayList<>();
                if (safePage > 0) {
                    navRow.add(InlineKeyboardFactory.button(
                            "◀️ Попередня",
                            CB_SENIOR_PICK_PAGE + CallbackIdEncoder.encode(requestId) + ":" + (safePage - 1)
                    ));
                }
                if (safePage < totalPages - 1) {
                    navRow.add(InlineKeyboardFactory.button(
                            "▶️ Далі",
                            CB_SENIOR_PICK_PAGE + CallbackIdEncoder.encode(requestId) + ":" + (safePage + 1)
                    ));
                }
                rows.add(navRow);
            }
        }
        rows.add(List.of(InlineKeyboardFactory.button("⬅️ Назад", CB_NAV_BACK + "SENIOR_PICK:" + CallbackIdEncoder.encode(requestId))));
        return InlineKeyboardFactory.rows(rows);
    }

    private String renderCandidatesSummary(SubstitutionRequest request, List<UserAccount> candidates) {
        StringBuilder builder = new StringBuilder();
        builder.append("🔎 Знайдено вільних на ")
                .append(DATE_FORMAT.format(request.getRequestDate()))
                .append(" — ")
                .append(candidates.size());
        if (!candidates.isEmpty()) {
            int index = 1;
            for (UserAccount candidate : candidates) {
                builder.append("\n").append(index++).append(") ").append(candidate.getLastName());
            }
        }
        return builder.toString();
    }

    private List<BotApiMethod<?>> buildCandidateOffers(SubstitutionRequest request, List<UserAccount> candidates) {
        List<BotApiMethod<?>> actions = new ArrayList<>();
        InlineKeyboardMarkup keyboard = InlineKeyboardFactory.rows(List.of(
                List.of(
                        InlineKeyboardFactory.button("✅ Так, можу", CB_CANDIDATE_ACCEPT + CallbackIdEncoder.encode(request.getId())),
                        InlineKeyboardFactory.button("❌ Ні", CB_CANDIDATE_DECLINE + CallbackIdEncoder.encode(request.getId()))
                )
        ));
        for (UserAccount candidate : candidates) {
            String text = """
                    🔁 Пропозиція підміни
                    📍 Локація: %s
                    📅 Дата: %s
                    Чи готові ви вийти?
                    """.formatted(request.getLocation().getName(), DATE_FORMAT.format(request.getRequestDate()));
            actions.add(notificationService.candidateOfferMessage(
                    request.getId(),
                    candidate.getTelegramChatId(),
                    text,
                    keyboard
            ));
        }
        return actions;
    }

    List<BotApiMethod<?>> notifyTmApproval(SubstitutionRequest request) {
        List<BotApiMethod<?>> actions = new ArrayList<>();
        UserAccount tmUser = request.getTmUser();
        if (tmUser == null) {
            Optional<UserAccount> tmOptional = substitutionService.findTmForRequest(request.getLocation());
            if (tmOptional.isPresent()) {
                tmUser = tmOptional.get();
            }
        }
        if (tmUser == null) {
            log.warn("TM not found for substitution approval. requesterId={}, locationId={}",
                    request.getRequester() != null ? request.getRequester().getId() : null,
                    request.getLocation() != null ? request.getLocation().getId() : null);
            actions.add(notificationService.sendMessage(
                    request.getRequester().getTelegramChatId(),
                    "⚠️ Не знайдено ТМ для підтвердження підміни. Зверніться до адміністратора.",
                    null
            ));
            return actions;
        }
        InlineKeyboardMarkup keyboard = InlineKeyboardFactory.rows(List.of(
                List.of(
                        InlineKeyboardFactory.button("✅ Підтвердити", CB_TM_APPROVE + CallbackIdEncoder.encode(request.getId())),
                        InlineKeyboardFactory.button("❌ Відхилити", CB_TM_REJECT + CallbackIdEncoder.encode(request.getId()))
                )
        ));
        String text = """
                ✅ Запит на підтвердження підміни
                👤 Хто просить: %s
                👤 Кандидат: %s
                📍 Локація: %s
                📅 Дата: %s
                Підтвердити підміну?
                """.formatted(
                request.getRequester().getLastName(),
                Optional.ofNullable(request.getProposedReplacementUser()).map(UserAccount::getLastName).orElse("-"),
                request.getLocation().getName(),
                DATE_FORMAT.format(request.getRequestDate())
        );
        actions.add(notificationService.sendMessage(
                tmUser.getTelegramChatId(),
                text,
                keyboard
        ));
        return actions;
    }

    private List<BotApiMethod<?>> notifyRequesterAndSeniors(SubstitutionRequest request) {
        List<BotApiMethod<?>> actions = new ArrayList<>();
        UserAccount replacement = request.getReplacementUser();
        if (replacement != null) {
            actions.add(notificationService.sendMessage(
                    request.getRequester().getTelegramChatId(),
                    "✅ Підміну знайдено: " + replacement.getLastName() + ". Дата " + DATE_FORMAT.format(request.getRequestDate()) + ".",
                    null
            ));
            substitutionService.findSeniorForRequest(request.getLocation())
                    .ifPresent(senior -> actions.add(notificationService.sendMessage(
                            senior.getTelegramChatId(),
                            "✅ Підміну підтверджено: " + replacement.getLastName() + " на " + DATE_FORMAT.format(request.getRequestDate()) + ".",
                            null
                    )));
        }
        return actions;
    }

    private List<BotApiMethod<?>> notifyTmRejection(SubstitutionRequest request) {
        List<BotApiMethod<?>> actions = new ArrayList<>();
        UserAccount candidate = request.getProposedReplacementUser();
        if (candidate != null) {
            actions.add(notificationService.sendMessage(
                    candidate.getTelegramChatId(),
                    "❌ ТМ не підтвердив підміну. Дякуємо за готовність.",
                    null
            ));
        }
        actions.add(notificationService.sendMessage(
                request.getRequester().getTelegramChatId(),
                "❌ ТМ відхилив підміну на " + DATE_FORMAT.format(request.getRequestDate()) + ".",
                null
        ));
        substitutionService.findSeniorForRequest(request.getLocation())
                .ifPresent(senior -> actions.add(notificationService.sendMessage(
                        senior.getTelegramChatId(),
                        buildTmRejectMenuText(request),
                        buildTmRejectMenuKeyboard(request.getId())
                )));
        return actions;
    }

    private List<BotApiMethod<?>> notifyCancellation(SubstitutionRequest request) {
        List<BotApiMethod<?>> actions = new ArrayList<>();
        List<SubstitutionRequestCandidate> notified = substitutionService.findNotifiedCandidates(request.getId());
        for (SubstitutionRequestCandidate candidate : notified) {
            Long chatId = candidate.getNotifiedChatId();
            if (chatId == null) {
                continue;
            }
            if (candidate.getNotifiedMessageId() != null) {
                actions.add(notificationService.editMessage(
                        chatId,
                        candidate.getNotifiedMessageId().intValue(),
                        "❌ Підміна скасована.",
                        null
                ));
            } else {
                actions.add(notificationService.sendMessage(chatId, "❌ Підміна скасована.", null));
            }
        }
        actions.add(notificationService.sendMessage(
                request.getRequester().getTelegramChatId(),
                "⚠️ Підміну скасовано. Ви працюєте за графіком.",
                null
        ));
        return actions;
    }

    private List<BotApiMethod<?>> notifyOtherCandidates(SubstitutionRequest request) {
        List<SubstitutionRequestCandidate> candidates = substitutionService.findExpiredCandidates(request.getId());
        return notifyOtherCandidates(candidates);
    }

    private List<BotApiMethod<?>> notifyOtherCandidates(List<SubstitutionRequestCandidate> candidates) {
        List<BotApiMethod<?>> actions = new ArrayList<>();
        for (SubstitutionRequestCandidate candidate : candidates) {
            if (candidate.getState() != SubstitutionCandidateState.EXPIRED
                    && candidate.getState() != SubstitutionCandidateState.DECLINED) {
                continue;
            }
            Long chatId = candidate.getNotifiedChatId();
            if (chatId == null) {
                continue;
            }
            if (candidate.getNotifiedMessageId() != null) {
                actions.add(notificationService.editMessage(
                        chatId,
                        candidate.getNotifiedMessageId().intValue(),
                        "✅ Підміну вже взяли, дякуємо.",
                        null
                ));
            } else {
                actions.add(notificationService.sendMessage(chatId, "✅ Підміну вже взяли, дякуємо.", null));
            }
        }
        return actions;
    }


    private EditMessageText editMessage(Message message, String text, InlineKeyboardMarkup keyboard) {
        return notificationService.editMessage(message.getChatId(), message.getMessageId(), text, keyboard);
    }

    private BotApiMethod<?> renderSellerMenu(Long telegramUserId, Long chatId, DateSelectionView view) {
        Optional<SubstitutionMenuSessionStore.MenuSession> session = substitutionMenuSessionStore.findSession(telegramUserId);
        if (session.isPresent()) {
            SubstitutionMenuSessionStore.MenuSession menuSession = session.get();
            return notificationService.editMenu(
                    telegramUserId,
                    menuSession.getChatId(),
                    menuSession.getMessageId().intValue(),
                    view.text(),
                    view.keyboard()
            );
        }
        return notificationService.sendMenu(telegramUserId, chatId, view.text(), view.keyboard());
    }

    private BotApiMethod<?> editSellerMenu(Long telegramUserId, Message fallbackMessage, String text, InlineKeyboardMarkup keyboard) {
        Optional<SubstitutionMenuSessionStore.MenuSession> session = substitutionMenuSessionStore.findSession(telegramUserId);
        if (session.isPresent()) {
            SubstitutionMenuSessionStore.MenuSession menuSession = session.get();
            return notificationService.editMenu(
                    telegramUserId,
                    menuSession.getChatId(),
                    menuSession.getMessageId().intValue(),
                    text,
                    keyboard
            );
        }
        return notificationService.editMessage(fallbackMessage.getChatId(), fallbackMessage.getMessageId(), text, keyboard);
    }

    private BotResponse withAnswer(CallbackQuery callbackQuery, BotResponse response) {
        boolean hasAnswer = response.actions().stream().anyMatch(AnswerCallbackQuery.class::isInstance);
        if (hasAnswer) {
            return response;
        }
        List<BotApiMethod<?>> actions = new ArrayList<>();
        actions.add(notificationService.answerCallbackQuery(callbackQuery.getId()));
        actions.addAll(response.actions());
        return new BotResponse(actions);
    }

    private UserAccount requireSenior(CallbackQuery callbackQuery) {
        UserAccount account = userAccountService.findByTelegramUserId(callbackQuery.getFrom().getId())
                .orElseThrow(() -> new IllegalStateException("Користувача не знайдено."));
        if (account.getStatus() != RegistrationStatus.APPROVED || account.getRole() != Role.SENIOR_SELLER) {
            throw new IllegalStateException("Недостатньо прав.");
        }
        return account;
    }

    private UserAccount requireTm(CallbackQuery callbackQuery) {
        UserAccount account = userAccountService.findByTelegramUserId(callbackQuery.getFrom().getId())
                .orElseThrow(() -> new IllegalStateException("Користувача не знайдено."));
        if (account.getStatus() != RegistrationStatus.APPROVED || account.getRole() != Role.TM) {
            throw new IllegalStateException("Недостатньо прав.");
        }
        return account;
    }

    private record DateSelectionView(String text, InlineKeyboardMarkup keyboard) {
    }

    private record ActiveRequestsView(String text, InlineKeyboardMarkup keyboard) {
    }
}
