package esvar.ua.workinghoursbot.service;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
public class SubstitutionDraftStore {

    private final Map<Long, Draft> drafts = new ConcurrentHashMap<>();

    public Draft createDraft(Long telegramUserId, LocalDate date, boolean urgent) {
        Draft draft = new Draft(UUID.randomUUID(), date, urgent);
        drafts.put(telegramUserId, draft);
        return draft;
    }

    public Optional<Draft> findDraft(Long telegramUserId, UUID draftId) {
        Draft draft = drafts.get(telegramUserId);
        if (draft == null || !draft.getId().equals(draftId)) {
            return Optional.empty();
        }
        return Optional.of(draft);
    }

    public void clearDraft(Long telegramUserId) {
        drafts.remove(telegramUserId);
    }

    @Getter
    public static class Draft {
        private final UUID id;
        private final LocalDate date;
        private final boolean urgent;

        public Draft(UUID id, LocalDate date, boolean urgent) {
            this.id = id;
            this.date = date;
            this.urgent = urgent;
        }
    }
}
