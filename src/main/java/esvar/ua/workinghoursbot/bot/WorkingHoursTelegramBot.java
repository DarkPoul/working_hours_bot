package esvar.ua.workinghoursbot.bot;

import esvar.ua.workinghoursbot.config.BotProperties;
import esvar.ua.workinghoursbot.service.ScheduleDraftStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkingHoursTelegramBot extends TelegramLongPollingBot {

    private final BotProperties botProperties;
    private final UpdateRouter updateRouter;
    private final ScheduleDraftStore scheduleDraftStore;

    @Override
    public String getBotUsername() {
        return botProperties.username();
    }

    @Override
    public String getBotToken() {
        return botProperties.token();
    }

    @Override
    public void onUpdateReceived(Update update) {
        BotResponse response = updateRouter.route(update);
        for (BotApiMethod<?> action : response.actions()) {
            try {
                executeAction(action);
            } catch (TelegramApiException ex) {
                handleExecutionFailure(action, ex);
            }
        }
    }

    private void executeAction(BotApiMethod<?> action) throws TelegramApiException {
        if (action instanceof SendMessage sendMessage) {
            Message sent = execute(sendMessage);
            if (sent != null) {
                scheduleDraftStore.updateMessageIdForChat(sent.getChatId(), sent.getMessageId());
            }
            return;
        }
        if (action instanceof EditMessageText editMessageText) {
            execute(editMessageText);
            return;
        }
        execute(action);
    }

    private void handleExecutionFailure(BotApiMethod<?> action, TelegramApiException ex) {
        log.error("Failed to execute bot action", ex);
        if (action instanceof EditMessageText editMessageText) {
            SendMessage fallback = new SendMessage();
            fallback.setChatId(editMessageText.getChatId());
            fallback.setText(editMessageText.getText());
            fallback.setParseMode(editMessageText.getParseMode());
            fallback.setReplyMarkup(editMessageText.getReplyMarkup());
            try {
                Message sent = execute(fallback);
                if (sent != null) {
                    scheduleDraftStore.updateMessageIdForChat(sent.getChatId(), sent.getMessageId());
                }
            } catch (TelegramApiException nested) {
                log.error("Failed to execute fallback bot action", nested);
            }
        }
    }
}
