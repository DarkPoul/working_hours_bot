package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.domain.RegistrationStatus;
import esvar.ua.workinghoursbot.domain.UserAccount;
import esvar.ua.workinghoursbot.repository.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationRequestService {

    private final UserAccountRepository userAccountRepository;

    public List<UserAccount> findPendingByTmUserId(Long tmUserId) {
        return userAccountRepository.findByStatusAndLocation_TmUserIdOrderByCreatedAtAsc(
                RegistrationStatus.PENDING_APPROVAL,
                tmUserId
        );
    }

    public Optional<UserAccount> findPendingByIdAndTmUserId(UUID id, Long tmUserId) {
        return userAccountRepository.findByIdAndStatusAndLocation_TmUserId(
                id,
                RegistrationStatus.PENDING_APPROVAL,
                tmUserId
        );
    }

    @Transactional
    public UserAccount approve(UserAccount request, Long approvedByTelegramUserId) {
        request.setStatus(RegistrationStatus.APPROVED);
        request.setApprovedByTelegramUserId(approvedByTelegramUserId);
        request.setApprovedAt(Instant.now());
        return userAccountRepository.save(request);
    }

    @Transactional
    public UserAccount reject(UserAccount request) {
        request.setStatus(RegistrationStatus.REJECTED);
        return userAccountRepository.save(request);
    }
}
