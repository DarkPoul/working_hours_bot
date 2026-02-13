package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.bot.BotResponse;
import esvar.ua.workinghoursbot.bot.KeyboardFactory;
import esvar.ua.workinghoursbot.domain.Role;
import esvar.ua.workinghoursbot.domain.SellerStatus;
import esvar.ua.workinghoursbot.domain.UserAccount;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@Service
public class AccessGuardService {

    public BotResponse check(UserAccount account, Long chatId, String text) {
        if (account == null) {
            return null;
        }
        if (account.isBlocked()) {
            return BotResponse.of(SendMessage.builder().chatId(chatId.toString())
                    .text("Ваш обліковий запис заблоковано.")
                    .build());
        }
        if (account.getRole() == Role.SELLER
                && (account.getSellerStatus() != SellerStatus.APPROVED || account.getLocation() == null)
                && !"🔄 Оновити".equals(text)
                && !"Скасувати запит".equals(text)
                && !"Обрати іншу локацію".equals(text)) {
            SendMessage message = SendMessage.builder().chatId(chatId.toString())
                    .text("Очікуйте рішення ТМ або керуйте запитом через меню.")
                    .build();
            message.setReplyMarkup(KeyboardFactory.pendingMenuKeyboard());
            return BotResponse.of(message);
        }
        return null;
    }
}
