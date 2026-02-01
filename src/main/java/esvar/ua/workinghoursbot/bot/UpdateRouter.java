package esvar.ua.workinghoursbot.bot;

import esvar.ua.workinghoursbot.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateRouter {

    private final RegistrationService registrationService;

    public BotResponse route(Update update) {
        if (update == null) {
            return BotResponse.empty();
        }

        if (update.hasMessage()) {
            return handleMessage(update.getMessage());
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
            return registrationService.startRegistration(telegramUserId, chatId);
        }

        if ("/cancel".equalsIgnoreCase(text)) {
            return registrationService.cancelRegistration(telegramUserId, chatId);
        }

        return registrationService.handleText(telegramUserId, chatId, text);
    }

    private BotResponse handleCallback(CallbackQuery callbackQuery) {
        if (callbackQuery == null || callbackQuery.getFrom() == null) {
            return BotResponse.empty();
        }

        log.info("Ignoring callback query for user {}", callbackQuery.getFrom().getId());
        return BotResponse.empty();
    }
}
