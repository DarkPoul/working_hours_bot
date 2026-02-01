package esvar.ua.workinghoursbot.repository;

import esvar.ua.workinghoursbot.domain.SubstitutionRequest;
import esvar.ua.workinghoursbot.domain.SubstitutionRequestStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface SubstitutionRequestRepository extends JpaRepository<SubstitutionRequest, UUID> {

    boolean existsByRequester_IdAndRequestDateAndStatusIn(
            UUID requesterId,
            LocalDate requestDate,
            Collection<SubstitutionRequestStatus> statuses
    );

    List<SubstitutionRequest> findByStatusInAndLocation_TmUserIdOrderByRequestDateAsc(
            Collection<SubstitutionRequestStatus> statuses,
            Long tmUserId
    );

    List<SubstitutionRequest> findByStatusInOrderByRequestDateAsc(Collection<SubstitutionRequestStatus> statuses);

    List<SubstitutionRequest> findByStatusAndLocation_IdAndRequestDateBetween(
            SubstitutionRequestStatus status,
            UUID locationId,
            LocalDate start,
            LocalDate end
    );

    @Lock(LockModeType.OPTIMISTIC)
    Optional<SubstitutionRequest> findWithLockById(UUID id);
}
