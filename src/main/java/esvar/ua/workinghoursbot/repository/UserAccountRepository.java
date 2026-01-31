package esvar.ua.workinghoursbot.repository;

import esvar.ua.workinghoursbot.domain.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByTelegramUserId(Long telegramUserId);
}
