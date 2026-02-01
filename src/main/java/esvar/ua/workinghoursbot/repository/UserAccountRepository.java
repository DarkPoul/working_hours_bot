package esvar.ua.workinghoursbot.repository;

import esvar.ua.workinghoursbot.domain.UserAccount;
import esvar.ua.workinghoursbot.domain.RegistrationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByTelegramUserId(Long telegramUserId);

    void deleteByTelegramUserId(Long telegramUserId);

    List<UserAccount> findByStatusAndLocation_TmUserIdOrderByCreatedAtAsc(RegistrationStatus status, Long tmUserId);

    Optional<UserAccount> findByIdAndStatusAndLocation_TmUserId(UUID id, RegistrationStatus status, Long tmUserId);
}
