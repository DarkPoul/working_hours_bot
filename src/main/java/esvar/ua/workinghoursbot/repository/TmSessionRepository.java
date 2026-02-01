package esvar.ua.workinghoursbot.repository;

import esvar.ua.workinghoursbot.domain.TmSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TmSessionRepository extends JpaRepository<TmSession, UUID> {
    Optional<TmSession> findByTelegramUserId(Long telegramUserId);
    void deleteByTelegramUserId(Long telegramUserId);
}
