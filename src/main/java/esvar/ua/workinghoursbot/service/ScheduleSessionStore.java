package esvar.ua.workinghoursbot.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ScheduleSessionStore {

    private final Map<Long, ScheduleSession> sessions = new ConcurrentHashMap<>();
    private final Map<Long, Long> pendingCalendarMessageByChatId = new ConcurrentHashMap<>();

    public ScheduleSession getOrCreate(Long telegramUserId) {
        return sessions.computeIfAbsent(telegramUserId, ScheduleSession::new);
    }

    public Optional<ScheduleSession> find(Long telegramUserId) {
        return Optional.ofNullable(sessions.get(telegramUserId));
    }

    public void clear(Long telegramUserId) {
        sessions.remove(telegramUserId);
    }

    public void markPendingCalendarMessage(Long chatId, Long telegramUserId) {
        if (chatId == null || telegramUserId == null) {
            return;
        }
        pendingCalendarMessageByChatId.put(chatId, telegramUserId);
    }

    public void updateMessageIdForChat(Long chatId, Integer messageId) {
        Long telegramUserId = pendingCalendarMessageByChatId.remove(chatId);
        if (telegramUserId == null) {
            return;
        }
        ScheduleSession session = sessions.get(telegramUserId);
        if (session == null) {
            return;
        }
        session.setCalendarChatId(chatId);
        session.setCalendarMessageId(messageId);
    }
}
