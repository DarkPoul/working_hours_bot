package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.bot.SubstitutionCandidateOfferMessage;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

@Service
public class TelegramNotificationService {

    public SendMessage sendMessage(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setReplyMarkup(keyboard);
        return message;
    }

    public SubstitutionCandidateOfferMessage candidateOfferMessage(UUID requestId,
                                                                   Long chatId,
                                                                   String text,
                                                                   InlineKeyboardMarkup keyboard) {
        SubstitutionCandidateOfferMessage message = new SubstitutionCandidateOfferMessage(requestId);
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setReplyMarkup(keyboard);
        return message;
    }

    public EditMessageText editMessage(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboard) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(text);
        message.setReplyMarkup(keyboard);
        return message;
    }

    public AnswerCallbackQuery answerCallbackQuery(String callbackQueryId, String text) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQueryId);
        answer.setText(text);
        return answer;
    }
}
