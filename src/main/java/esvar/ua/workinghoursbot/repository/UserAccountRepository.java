package esvar.ua.workinghoursbot.repository;

import esvar.ua.workinghoursbot.domain.UserAccount;
import esvar.ua.workinghoursbot.domain.RegistrationStatus;
import esvar.ua.workinghoursbot.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByTelegramUserId(Long telegramUserId);

    void deleteByTelegramUserId(Long telegramUserId);

    List<UserAccount> findByStatusAndLocation_TmUserIdOrderByCreatedAtAsc(RegistrationStatus status, Long tmUserId);

    Optional<UserAccount> findByIdAndStatusAndLocation_TmUserId(UUID id, RegistrationStatus status, Long tmUserId);

    List<UserAccount> findByStatusAndRoleInAndLocation_Id(
            RegistrationStatus status,
            List<Role> roles,
            UUID locationId
    );

    List<UserAccount> findByStatusAndRoleInAndLocation_TmUserId(
            RegistrationStatus status,
            List<Role> roles,
            Long tmUserId
    );

    List<UserAccount> findByStatusAndRoleIn(RegistrationStatus status, List<Role> roles);

    List<UserAccount> findByStatusAndRoleAndLocation_Id(
            RegistrationStatus status,
            Role role,
            UUID locationId
    );

    Optional<UserAccount> findFirstByStatusAndRoleAndLocation_TmUserIdOrderByCreatedAtAsc(
            RegistrationStatus status,
            Role role,
            Long tmUserId
    );

    Optional<UserAccount> findFirstByStatusAndRoleAndLocation_IdOrderByCreatedAtAsc(
            RegistrationStatus status,
            Role role,
            UUID locationId
    );

    Optional<UserAccount> findFirstByStatusAndRoleOrderByCreatedAtAsc(
            RegistrationStatus status,
            Role role
    );
}
