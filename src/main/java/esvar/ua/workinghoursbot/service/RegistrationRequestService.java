package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.domain.AuditEventType;
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
    private final AuditService auditService;

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
        UserAccount saved = userAccountRepository.save(request);
        userAccountRepository.findByTelegramUserId(approvedByTelegramUserId)
                .ifPresent(actor -> auditService.log(
                        AuditEventType.USER_APPROVED,
                        actor.getId(),
                        saved.getId(),
                        saved.getLocation() != null ? saved.getLocation().getId() : null,
                        "Користувач: %s | Локація: %s".formatted(
                                saved.getLastName(),
                                saved.getLocation() != null ? saved.getLocation().getName() : "-"
                        )
                ));
        return saved;
    }

    @Transactional
    public UserAccount reject(UserAccount request) {
        request.setStatus(RegistrationStatus.REJECTED);
        return userAccountRepository.save(request);
    }
}
