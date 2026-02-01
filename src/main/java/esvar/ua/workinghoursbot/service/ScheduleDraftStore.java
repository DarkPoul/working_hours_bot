package esvar.ua.workinghoursbot.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ScheduleDraftStore {

    private final Map<ScheduleDraftKey, ScheduleDraft> drafts = new ConcurrentHashMap<>();
    private final Map<Long, ScheduleDraftKey> lastDraftByChatId = new ConcurrentHashMap<>();

    public ScheduleDraft saveDraft(ScheduleDraft draft) {
        drafts.keySet().removeIf(key -> key.telegramUserId().equals(draft.getTelegramUserId())
                && key.mode() == draft.getMode());
        ScheduleDraftKey key = new ScheduleDraftKey(
                draft.getTelegramUserId(),
                draft.getLocationId(),
                draft.getYearMonth(),
                draft.getMode()
        );
        drafts.put(key, draft);
        if (draft.getMessageChatId() != null) {
            lastDraftByChatId.put(draft.getMessageChatId(), key);
        }
        return draft;
    }

    public Optional<ScheduleDraft> findDraft(Long telegramUserId, ScheduleMode mode) {
        return drafts.values().stream()
                .filter(draft -> draft.getTelegramUserId().equals(telegramUserId) && draft.getMode() == mode)
                .findFirst();
    }

    public void removeDraft(ScheduleDraft draft) {
        ScheduleDraftKey key = new ScheduleDraftKey(
                draft.getTelegramUserId(),
                draft.getLocationId(),
                draft.getYearMonth(),
                draft.getMode()
        );
        drafts.remove(key);
    }

    public void updateMessageIdForChat(Long chatId, Integer messageId) {
        ScheduleDraftKey key = lastDraftByChatId.get(chatId);
        if (key == null) {
            return;
        }
        ScheduleDraft draft = drafts.get(key);
        if (draft != null) {
            draft.setMessageId(messageId);
            draft.setMessageChatId(chatId);
        }
    }

    public void markChatDraft(Long chatId, ScheduleDraft draft) {
        if (chatId == null) {
            return;
        }
        ScheduleDraftKey key = new ScheduleDraftKey(
                draft.getTelegramUserId(),
                draft.getLocationId(),
                draft.getYearMonth(),
                draft.getMode()
        );
        drafts.put(key, draft);
        lastDraftByChatId.put(chatId, key);
    }
}
