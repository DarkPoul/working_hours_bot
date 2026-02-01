package esvar.ua.workinghoursbot.repository;

import esvar.ua.workinghoursbot.domain.SubstitutionRequest;
import esvar.ua.workinghoursbot.domain.SubstitutionRequestStatus;
import java.time.LocalDate;
import java.util.Collection;
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

    @Lock(LockModeType.OPTIMISTIC)
    Optional<SubstitutionRequest> findWithLockById(UUID id);
}
