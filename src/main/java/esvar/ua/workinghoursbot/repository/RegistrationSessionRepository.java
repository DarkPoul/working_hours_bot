package esvar.ua.workinghoursbot.repository;

import esvar.ua.workinghoursbot.domain.RegistrationSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationSessionRepository extends JpaRepository<RegistrationSession, Long> {

    Optional<RegistrationSession> findByTelegramUserId(Long telegramUserId);

    void deleteByTelegramUserId(Long telegramUserId);
}
