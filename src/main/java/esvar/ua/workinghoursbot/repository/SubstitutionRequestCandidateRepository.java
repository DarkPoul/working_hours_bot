package esvar.ua.workinghoursbot.repository;

import esvar.ua.workinghoursbot.domain.SubstitutionCandidateState;
import esvar.ua.workinghoursbot.domain.SubstitutionRequestCandidate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubstitutionRequestCandidateRepository extends JpaRepository<SubstitutionRequestCandidate, UUID> {

    Optional<SubstitutionRequestCandidate> findByRequest_IdAndCandidate_Id(UUID requestId, UUID candidateId);

    Optional<SubstitutionRequestCandidate> findByRequest_IdAndNotifiedChatId(UUID requestId, Long notifiedChatId);

    List<SubstitutionRequestCandidate> findByRequest_IdAndState(UUID requestId, SubstitutionCandidateState state);

    List<SubstitutionRequestCandidate> findByRequest_IdAndStateIn(
            UUID requestId,
            Collection<SubstitutionCandidateState> states
    );
}
