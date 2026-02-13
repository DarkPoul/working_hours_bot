package esvar.ua.workinghoursbot.bot;

import esvar.ua.workinghoursbot.domain.RegistrationStatus;
import esvar.ua.workinghoursbot.domain.Role;
import esvar.ua.workinghoursbot.domain.UserAccount;
import esvar.ua.workinghoursbot.domain.AuditEventType;
import esvar.ua.workinghoursbot.service.RegistrationService;
import esvar.ua.workinghoursbot.service.ScheduleInteractionHandler;
import esvar.ua.workinghoursbot.service.ShiftConfirmationService;
import esvar.ua.workinghoursbot.service.LocationInfoService;
import esvar.ua.workinghoursbot.service.AuditService;
import esvar.ua.workinghoursbot.service.SubstitutionInteractionHandler;
import esvar.ua.workinghoursbot.service.SeniorSellerMenuService;
import esvar.ua.workinghoursbot.service.TmMenuService;
import esvar.ua.workinghoursbot.service.UserAccountService;
import esvar.ua.workinghoursbot.service.AccessGuardService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UpdateRouter {

    private final RegistrationService registrationService;
    private final UserAccountService userAccountService;
    private final TmMenuService tmMenuService;
    private final ScheduleInteractionHandler scheduleInteractionHandler;
    private final SubstitutionInteractionHandler substitutionInteractionHandler;
    private final SeniorSellerMenuService seniorSellerMenuService;
    private final ShiftConfirmationService shiftConfirmationService;
    private final LocationInfoService locationInfoService;
    private final AuditService auditService;
    private final AccessGuardService accessGuardService;

    public BotResponse route(Update update) {
        if (update == null) {
            return BotResponse.empty();
        }

        if (update.hasMessage()) {
            return handleMessage(update.getMessage());
        }

        if (update.hasCallbackQuery()) {
            return handleCallback(update.getCallbackQuery());
        }

        return BotResponse.empty();
    }

    private BotResponse handleMessage(Message message) {
        if (message == null || message.getFrom() == null) {
            return BotResponse.empty();
        }

        Long telegramUserId = message.getFrom().getId();
        Long chatId = message.getChatId();
        String text = message.getText();

        if (text == null || text.isBlank()) {
            return BotResponse.of(org.telegram.telegrambots.meta.api.methods.send.SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Підтримуються лише текстові повідомлення.")
                    .build());
        }

        if ("/start".equalsIgnoreCase(text)) {
            return handleStartCommand(telegramUserId, chatId);
        }

        if ("/cancel".equalsIgnoreCase(text)) {
            return registrationService.cancelRegistration(telegramUserId, chatId);
        }

        return handleText(telegramUserId, chatId, text);
    }

    private BotResponse handleCallback(CallbackQuery callbackQuery) {
        if (callbackQuery == null || callbackQuery.getFrom() == null) {
            return BotResponse.empty();
        }

        BotResponse tmResponse = tmMenuService.handleCallback(callbackQuery);
        if (!tmResponse.actions().isEmpty()) {
            return tmResponse;
        }

        BotResponse substitutionResponse = substitutionInteractionHandler.handleCallback(callbackQuery);
        if (!substitutionResponse.actions().isEmpty()) {
            return substitutionResponse;
        }

        BotResponse response = scheduleInteractionHandler.handleCallback(callbackQuery);
        if (!response.actions().isEmpty()) {
            return response;
        }
        log.info("Ignoring callback query for user {}", callbackQuery.getFrom().getId());
        return response;
    }

    private BotResponse handleStartCommand(Long telegramUserId, Long chatId) {
        Optional<UserAccount> accountOptional = userAccountService.findByTelegramUserId(telegramUserId);
        auditService.log(
                AuditEventType.USER_START,
                accountOptional.map(UserAccount::getId).orElse(null),
                null,
                accountOptional.map(account -> account.getLocation() == null ? null : account.getLocation().getId()).orElse(null),
                accountOptional.map(account -> "Користувач: " + account.getLastName())
                        .orElse("TelegramUserId: " + telegramUserId)
        );
        return userAccountService.findByTelegramUserId(telegramUserId)
                .filter(account -> account.getStatus() == RegistrationStatus.APPROVED)
                .map(account -> {
                    if (account.getRole() == Role.TM) {
                        return tmMenuService.showMainMenu(telegramUserId, chatId);
                    }
                    return registrationService.startRegistration(telegramUserId, chatId);
                })
                .orElseGet(() -> registrationService.startRegistration(telegramUserId, chatId));
    }

    private BotResponse handleText(Long telegramUserId, Long chatId, String text) {
        UserAccount account = userAccountService.findByTelegramUserId(telegramUserId).orElse(null);
        BotResponse guardResponse = accessGuardService.check(account, chatId, text);
        if (guardResponse != null) {
            return guardResponse;
        }
        if (account != null && account.getStatus() == RegistrationStatus.APPROVED && account.getRole() != Role.TM) {
            BotResponse confirmationResponse = shiftConfirmationService.handleResponse(telegramUserId, chatId, text);
            if (!confirmationResponse.actions().isEmpty()) {
                return confirmationResponse;
            }
        }
        if (account != null && account.getStatus() == RegistrationStatus.APPROVED) {
            BotResponse locationResponse = locationInfoService.showMyLocation(account, chatId, text);
            if (!locationResponse.actions().isEmpty()) {
                return locationResponse;
            }
        }
        if (account != null && account.getStatus() == RegistrationStatus.APPROVED && account.getRole() == Role.TM) {
            return tmMenuService.handleText(telegramUserId, chatId, text);
        }
        if (account != null && account.getStatus() == RegistrationStatus.APPROVED
                && account.getRole() == Role.SENIOR_SELLER) {
            BotResponse seniorResponse = seniorSellerMenuService.handleText(telegramUserId, chatId, text);
            if (!seniorResponse.actions().isEmpty()) {
                return seniorResponse;
            }
        }
        if (account != null && account.getStatus() == RegistrationStatus.APPROVED && account.getRole() != Role.TM) {
            BotResponse substitutionResponse = substitutionInteractionHandler.handleMessage(telegramUserId, chatId, text);
            if (!substitutionResponse.actions().isEmpty()) {
                return substitutionResponse;
            }
            BotResponse response = scheduleInteractionHandler.handleMessage(telegramUserId, chatId, text);
            if (!response.actions().isEmpty()) {
                return response;
            }
        }
        return registrationService.handleText(telegramUserId, chatId, text);
    }
}
