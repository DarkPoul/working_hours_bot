package esvar.ua.workinghoursbot.bot;

import esvar.ua.workinghoursbot.config.BotProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkingHoursTelegramBot extends TelegramLongPollingBot {

    private final BotProperties botProperties;
    private final UpdateRouter updateRouter;

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
                execute(action);
            } catch (TelegramApiException ex) {
                log.error("Failed to execute bot action", ex);
            }
        }
    }
}
