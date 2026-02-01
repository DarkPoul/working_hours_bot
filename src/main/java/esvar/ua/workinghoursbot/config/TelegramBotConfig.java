package esvar.ua.workinghoursbot.config;

import esvar.ua.workinghoursbot.bot.WorkingHoursTelegramBot;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(value = "bot.enabled", havingValue = "true", matchIfMissing = true)
public class TelegramBotConfig {

    private final WorkingHoursTelegramBot workingHoursTelegramBot;

    @Bean
    public TelegramBotsApi telegramBotsApi() throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(workingHoursTelegramBot);
        return botsApi;
    }
}
