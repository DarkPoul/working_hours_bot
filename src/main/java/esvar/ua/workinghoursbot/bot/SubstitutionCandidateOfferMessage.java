package esvar.ua.workinghoursbot.bot;

import java.util.UUID;
import lombok.Getter;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@Getter
public class SubstitutionCandidateOfferMessage extends SendMessage {

    private final UUID requestId;

    public SubstitutionCandidateOfferMessage(UUID requestId) {
        this.requestId = requestId;
    }
}
