package esvar.ua.workinghoursbot.bot;

import lombok.Getter;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@Getter
public class SubstitutionMenuMessage extends SendMessage {

    private final Long telegramUserId;

    public SubstitutionMenuMessage(Long telegramUserId) {
        this.telegramUserId = telegramUserId;
    }
}
