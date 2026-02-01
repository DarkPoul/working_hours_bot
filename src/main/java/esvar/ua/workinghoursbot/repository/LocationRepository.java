package esvar.ua.workinghoursbot.repository;

import esvar.ua.workinghoursbot.domain.Location;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {

    // Тепер цей метод працюватиме, оскільки в сутності Location з'явилося поле active
    Optional<Location> findByCodeAndActiveTrue(String code);

    Page<Location> findByActiveTrueOrderBySortOrderAscNameAsc(Pageable pageable);

    @Query("""
            select l from UserAccount tm
            join tm.managedLocations l
            where tm.id = :tmId and l.active = true
            order by l.name asc
            """)
    List<Location> findActiveByManagedTmId(@Param("tmId") UUID tmId);

    @Query("""
            select l from UserAccount tm
            join tm.managedLocations l
            where tm.id = :tmId and l.id = :locationId and l.active = true
            """)
    Optional<Location> findActiveByIdAndManagedTmId(@Param("locationId") UUID locationId, @Param("tmId") UUID tmId);

    @Query("""
            select l from Location l
            where l.active = true
            and l not in (select ml from UserAccount tm join tm.managedLocations ml where tm.id = :tmId)
            order by l.name asc
            """)
    List<Location> findActiveAvailableForTm(@Param("tmId") UUID tmId);

    boolean existsByCode(String code);

    Optional<Boolean> findScheduleEditEnabledById(UUID id);

    @Query("""
            select l.scheduleEditEnabled from UserAccount account
            join account.location l
            where account.id = :accountId
            """)
    Optional<Boolean> findScheduleEditEnabledByAccount_Id(@Param("accountId") UUID id);
}
