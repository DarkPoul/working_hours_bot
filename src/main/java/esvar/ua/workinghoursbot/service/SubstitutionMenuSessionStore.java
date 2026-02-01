package esvar.ua.workinghoursbot.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
public class SubstitutionMenuSessionStore {

    private final Map<Long, MenuSession> sessions = new ConcurrentHashMap<>();

    public Optional<MenuSession> findSession(Long telegramUserId) {
        return Optional.ofNullable(sessions.get(telegramUserId));
    }

    public void updateSession(Long telegramUserId, Long chatId, Integer messageId) {
        if (telegramUserId == null || chatId == null || messageId == null) {
            return;
        }
        sessions.put(telegramUserId, new MenuSession(chatId, messageId.longValue()));
    }

    @Getter
    public static class MenuSession {
        private final Long chatId;
        private final Long messageId;

        public MenuSession(Long chatId, Long messageId) {
            this.chatId = chatId;
            this.messageId = messageId;
        }
    }
}
