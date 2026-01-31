package esvar.ua.workinghoursbot.bot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;

public record BotResponse(List<BotApiMethod<?>> actions) {

    public static BotResponse empty() {
        return new BotResponse(Collections.emptyList());
    }

    public static BotResponse of(BotApiMethod<?>... actions) {
        return new BotResponse(new ArrayList<>(Arrays.asList(actions)));
    }
}
