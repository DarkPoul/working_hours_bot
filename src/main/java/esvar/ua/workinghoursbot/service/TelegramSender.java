package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.bot.WorkingHoursTelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramSender {

    private final ObjectProvider<WorkingHoursTelegramBot> telegramBotProvider;

    public void send(SendMessage message) {
        if (message == null) {
            return;
        }
        WorkingHoursTelegramBot telegramBot = telegramBotProvider.getIfAvailable();
        if (telegramBot == null) {
            return;
        }
        try {
            telegramBot.execute(message);
        } catch (TelegramApiException ex) {
            log.warn("Не вдалося надіслати повідомлення в Telegram.", ex);
        }
    }

    public void send(BotApiMethod<?> method) {
        if (method == null) {
            return;
        }
        WorkingHoursTelegramBot telegramBot = telegramBotProvider.getIfAvailable();
        if (telegramBot == null) {
            return;
        }
        try {
            telegramBot.execute(method);
        } catch (TelegramApiException ex) {
            log.warn("Не вдалося виконати команду Telegram.", ex);
        }
    }
}
