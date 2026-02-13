package esvar.ua.workinghoursbot.repository;

import esvar.ua.workinghoursbot.domain.JoinRequest;
import esvar.ua.workinghoursbot.domain.JoinRequestStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JoinRequestRepository extends JpaRepository<JoinRequest, UUID> {
    Optional<JoinRequest> findFirstBySeller_IdAndStatus(UUID sellerId, JoinRequestStatus status);

    boolean existsBySeller_IdAndStatus(UUID sellerId, JoinRequestStatus status);

    List<JoinRequest> findByTm_IdAndStatusOrderByCreatedAtAsc(UUID tmId, JoinRequestStatus status);
}
