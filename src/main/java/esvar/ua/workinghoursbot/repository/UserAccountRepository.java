package esvar.ua.workinghoursbot.repository;

import esvar.ua.workinghoursbot.domain.UserAccount;
import esvar.ua.workinghoursbot.domain.RegistrationStatus;
import esvar.ua.workinghoursbot.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByTelegramUserId(Long telegramUserId);

    void deleteByTelegramUserId(Long telegramUserId);

    List<UserAccount> findByStatusAndRoleInAndLocation_Id(
            RegistrationStatus status,
            List<Role> roles,
            UUID locationId
    );

    List<UserAccount> findByStatusAndRoleIn(RegistrationStatus status, List<Role> roles);

    List<UserAccount> findByStatusAndRoleAndLocation_Id(
            RegistrationStatus status,
            Role role,
            UUID locationId
    );

    List<UserAccount> findByStatusAndRoleAndLocation_IdOrderByCreatedAtAsc(
            RegistrationStatus status,
            Role role,
            UUID locationId
    );

    List<UserAccount> findByStatusAndRoleAndApprovedByTelegramUserIdOrderByCreatedAtAsc(
            RegistrationStatus status,
            Role role,
            Long approvedByTelegramUserId
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

    List<UserAccount> findByStatusAndRoleOrderByCreatedAtAsc(
            RegistrationStatus status,
            Role role
    );

    long countByStatusAndRoleAndLocation_Id(
            RegistrationStatus status,
            Role role,
            UUID locationId
    );

    @Query("""
            select u from UserAccount u
            join u.managedLocations l
            where u.role = esvar.ua.workinghoursbot.domain.Role.TM
            and u.status = esvar.ua.workinghoursbot.domain.RegistrationStatus.APPROVED
            and l.id = :locationId
            """)
    Optional<UserAccount> findActiveTmByManagedLocation(@Param("locationId") UUID locationId);

    @Query("""
            select ua from UserAccount ua
            where ua.status = :status
            and ua.location in (select l from UserAccount tm join tm.managedLocations l where tm.id = :tmId)
            order by ua.createdAt asc
            """)
    List<UserAccount> findByStatusAndLocationManagedByTmOrderByCreatedAtAsc(
            @Param("status") RegistrationStatus status,
            @Param("tmId") UUID tmId
    );

    @Query("""
            select ua from UserAccount ua
            where ua.id = :id and ua.status = :status
            and ua.location in (select l from UserAccount tm join tm.managedLocations l where tm.id = :tmId)
            """)
    Optional<UserAccount> findByIdAndStatusAndLocationManagedByTm(
            @Param("id") UUID id,
            @Param("status") RegistrationStatus status,
            @Param("tmId") UUID tmId
    );

    @Query("""
            select ua from UserAccount ua
            where ua.status = :status
            and ua.role in :roles
            and ua.location in (select l from UserAccount tm join tm.managedLocations l where tm.id = :tmId)
            """)
    List<UserAccount> findByStatusAndRoleInAndLocationManagedByTm(
            @Param("status") RegistrationStatus status,
            @Param("roles") List<Role> roles,
            @Param("tmId") UUID tmId
    );

    @Query("""
            select count(ua) from UserAccount ua
            where ua.status = :status
            and ua.role = :role
            and ua.location in (select l from UserAccount tm join tm.managedLocations l where tm.id = :tmId)
            """)
    long countByStatusAndRoleAndLocationManagedByTm(
            @Param("status") RegistrationStatus status,
            @Param("role") Role role,
            @Param("tmId") UUID tmId
    );

    @Query("""
            select ua from UserAccount ua
            where ua.status = :status
            and ua.role = :role
            and ua.location in (select l from UserAccount tm join tm.managedLocations l where tm.id = :tmId)
            order by ua.createdAt asc
            """)
    List<UserAccount> findByStatusAndRoleAndLocationManagedByTmOrderByCreatedAtAsc(
            @Param("status") RegistrationStatus status,
            @Param("role") Role role,
            @Param("tmId") UUID tmId
    );

    @Query("""
            select ua from UserAccount ua
            where ua.status = :status
            and ua.role = :role
            and ua.location in (select l from UserAccount tm join tm.managedLocations l where tm.id = :tmId)
            order by ua.createdAt asc
            """)
    Optional<UserAccount> findFirstByStatusAndRoleAndLocationManagedByTmOrderByCreatedAtAsc(
            @Param("status") RegistrationStatus status,
            @Param("role") Role role,
            @Param("tmId") UUID tmId
    );

    @Query("""
            select ua from UserAccount ua
            where ua.id = :id and ua.status = :status and ua.role = :role
            and ua.location in (select l from UserAccount tm join tm.managedLocations l where tm.id = :tmId)
            """)
    Optional<UserAccount> findByIdAndStatusAndRoleAndLocationManagedByTm(
            @Param("id") UUID id,
            @Param("status") RegistrationStatus status,
            @Param("role") Role role,
            @Param("tmId") UUID tmId
    );

    long countByRoleAndSellerStatusAndLocation_Id(Role role, esvar.ua.workinghoursbot.domain.SellerStatus sellerStatus, UUID locationId);

    java.util.List<UserAccount> findByRoleAndSellerStatusAndLocation_Id(Role role, esvar.ua.workinghoursbot.domain.SellerStatus sellerStatus, UUID locationId);
}
