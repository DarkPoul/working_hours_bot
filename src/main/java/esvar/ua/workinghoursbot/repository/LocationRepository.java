package esvar.ua.workinghoursbot.repository;

import esvar.ua.workinghoursbot.domain.Location;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {

    // Тепер цей метод працюватиме, оскільки в сутності Location з'явилося поле active
    Optional<Location> findByCodeAndActiveTrue(String code);

    Page<Location> findByActiveTrueOrderBySortOrderAscNameAsc(Pageable pageable);

    List<Location> findByTmUserIdAndActiveTrueOrderByNameAsc(Long tmUserId);

    Optional<Location> findByIdAndTmUserId(UUID id, Long tmUserId);

    boolean existsByCode(String code);
}
