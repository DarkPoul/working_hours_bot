package esvar.ua.workinghoursbot.repository;

import esvar.ua.workinghoursbot.domain.Location;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, Long> {

    Page<Location> findByActiveTrueOrderBySortOrderAscNameAsc(Pageable pageable);

    Optional<Location> findByCodeAndActiveTrue(String code);
}
