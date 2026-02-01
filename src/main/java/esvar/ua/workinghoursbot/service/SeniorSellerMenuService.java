package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.bot.BotResponse;
import esvar.ua.workinghoursbot.bot.KeyboardFactory;
import esvar.ua.workinghoursbot.domain.RegistrationStatus;
import esvar.ua.workinghoursbot.domain.Role;
import esvar.ua.workinghoursbot.domain.TmSession;
import esvar.ua.workinghoursbot.domain.TmState;
import esvar.ua.workinghoursbot.domain.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@Service
@RequiredArgsConstructor
@Transactional
public class SeniorSellerMenuService {

    private static final String BUTTON_REQUESTS = "Заявки продавців";
    private static final String BUTTON_BACK = "Назад";
    private static final String BUTTON_YES = "Так";
    private static final String BUTTON_REJECT = "Заборонити";

    private final RegistrationRequestService registrationRequestService;
    private final TmSessionService tmSessionService;
    private final UserAccountService userAccountService;
    private final MainMenuService mainMenuService;

    public BotResponse handleText(Long telegramUserId, Long chatId, String text) {
        if (text == null) {
            return BotResponse.empty();
        }
        Optional<UserAccount> seniorSeller = resolveSeniorSeller(telegramUserId);
        if (seniorSeller.isEmpty()) {
            return BotResponse.empty();
        }

        TmSession session = getOrCreateSession(telegramUserId);
        if (BUTTON_REQUESTS.equalsIgnoreCase(text)) {
            return showRequestsList(session, chatId, seniorSeller.get());
        }

        return switch (session.getState()) {
            case REQUESTS_LIST -> handleRequestsList(session, chatId, text, seniorSeller.get());
            case REQUEST_DETAILS -> handleRequestDetails(session, chatId, text, seniorSeller.get());
            case MAIN_MENU -> showMainMenu(session, chatId, seniorSeller.get());
            default -> BotResponse.empty();
        };
    }

    private BotResponse handleRequestsList(TmSession session, Long chatId, String text, UserAccount seniorSeller) {
        if (BUTTON_BACK.equalsIgnoreCase(text)) {
            return showMainMenu(session, chatId, seniorSeller);
        }

        List<UserAccount> requests = resolveTmAccount(seniorSeller)
                .map(tm -> registrationRequestService.findPendingByTmIdAndRole(tm.getId(), Role.SELLER))
                .orElseGet(List::of);
        Optional<UserAccount> request = findRequestByButton(requests, text);
        if (request.isEmpty()) {
            SendMessage message = buildMessage(chatId, "Оберіть заявку зі списку.");
            message.setReplyMarkup(KeyboardFactory.listWithBackKeyboard(formatRequests(requests)));
            return BotResponse.of(message);
        }
        return showRequestDetails(session, chatId, request.get());
    }

    private BotResponse handleRequestDetails(TmSession session, Long chatId, String text, UserAccount seniorSeller) {
        if (BUTTON_BACK.equalsIgnoreCase(text)) {
            return showRequestsList(session, chatId, seniorSeller);
        }
        if (BUTTON_YES.equalsIgnoreCase(text)) {
            return approveRequest(session, chatId, seniorSeller);
        }
        if (BUTTON_REJECT.equalsIgnoreCase(text)) {
            return rejectRequest(session, chatId, seniorSeller);
        }

        UUID requestId = session.getSelectedRequestId();
        if (requestId == null) {
            return showRequestsList(session, chatId, seniorSeller);
        }
        Optional<UserAccount> request = resolveTmAccount(seniorSeller)
                .flatMap(tm -> registrationRequestService.findPendingByIdAndTmIdAndRole(
                        requestId,
                        tm.getId(),
                        Role.SELLER
                ));
        if (request.isEmpty()) {
            return showRequestsList(session, chatId, seniorSeller);
        }
        return showRequestDetails(session, chatId, request.get());
    }

    private BotResponse showMainMenu(TmSession session, Long chatId, UserAccount seniorSeller) {
        session.setState(TmState.MAIN_MENU);
        clearSelections(session);
        tmSessionService.save(session);

        SendMessage message = buildMessage(chatId, "Оберіть дію:");
        message.setReplyMarkup(mainMenuService.mainMenuKeyboard(seniorSeller));
        return BotResponse.of(message);
    }

    private BotResponse showRequestsList(TmSession session, Long chatId, UserAccount seniorSeller) {
        session.setState(TmState.REQUESTS_LIST);
        clearSelections(session);
        tmSessionService.save(session);

        List<UserAccount> requests = resolveTmAccount(seniorSeller)
                .map(tm -> registrationRequestService.findPendingByTmIdAndRole(tm.getId(), Role.SELLER))
                .orElseGet(List::of);
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

    private BotResponse approveRequest(TmSession session, Long chatId, UserAccount seniorSeller) {
        UUID requestId = session.getSelectedRequestId();
        if (requestId != null) {
            resolveTmAccount(seniorSeller)
                    .flatMap(tm -> registrationRequestService.findPendingByIdAndTmIdAndRole(
                            requestId,
                            tm.getId(),
                            Role.SELLER
                    ))
                    .ifPresent(request -> registrationRequestService.approve(request, session.getTelegramUserId()));
        }
        session.setSelectedRequestId(null);
        tmSessionService.save(session);

        SendMessage done = buildMessage(chatId, "Готово. Працівника додано.");
        BotResponse listResponse = showRequestsList(session, chatId, seniorSeller);
        return BotResponse.of(done, listResponse.actions().get(0));
    }

    private BotResponse rejectRequest(TmSession session, Long chatId, UserAccount seniorSeller) {
        UUID requestId = session.getSelectedRequestId();
        if (requestId != null) {
            resolveTmAccount(seniorSeller)
                    .flatMap(tm -> registrationRequestService.findPendingByIdAndTmIdAndRole(
                            requestId,
                            tm.getId(),
                            Role.SELLER
                    ))
                    .ifPresent(registrationRequestService::reject);
        }
        session.setSelectedRequestId(null);
        tmSessionService.save(session);

        SendMessage done = buildMessage(chatId, "Заявку відхилено.");
        BotResponse listResponse = showRequestsList(session, chatId, seniorSeller);
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

    private Optional<UserAccount> resolveSeniorSeller(Long telegramUserId) {
        return userAccountService.findByTelegramUserId(telegramUserId)
                .filter(account -> account.getRole() == Role.SENIOR_SELLER
                        && account.getStatus() == RegistrationStatus.APPROVED);
    }

    private Optional<UserAccount> resolveTmAccount(UserAccount seniorSeller) {
        if (seniorSeller == null || seniorSeller.getLocation() == null) {
            return Optional.empty();
        }
        return userAccountService.findActiveTmByManagedLocation(seniorSeller.getLocation().getId());
    }
}
