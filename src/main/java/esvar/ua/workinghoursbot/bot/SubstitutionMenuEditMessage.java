package esvar.ua.workinghoursbot.bot;

import lombok.Getter;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;

@Getter
public class SubstitutionMenuEditMessage extends EditMessageText {

    private final Long telegramUserId;

    public SubstitutionMenuEditMessage(Long telegramUserId) {
        this.telegramUserId = telegramUserId;
    }
}
