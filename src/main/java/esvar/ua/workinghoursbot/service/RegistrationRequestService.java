package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.domain.AuditEventType;
import esvar.ua.workinghoursbot.domain.RegistrationStatus;
import esvar.ua.workinghoursbot.domain.Role;
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

    public List<UserAccount> findPendingByTmId(UUID tmUserId) {
        return userAccountRepository.findByStatusAndLocationManagedByTmOrderByCreatedAtAsc(
                RegistrationStatus.PENDING_APPROVAL,
                tmUserId
        );
    }

    public List<UserAccount> findPendingByTmIdAndRole(UUID tmUserId, Role role) {
        return userAccountRepository.findByStatusAndRoleAndLocationManagedByTmOrderByCreatedAtAsc(
                RegistrationStatus.PENDING_APPROVAL,
                role,
                tmUserId
        );
    }

    public Optional<UserAccount> findPendingByIdAndTmId(UUID id, UUID tmUserId) {
        return userAccountRepository.findByIdAndStatusAndLocationManagedByTm(
                id,
                RegistrationStatus.PENDING_APPROVAL,
                tmUserId
        );
    }

    public Optional<UserAccount> findPendingByIdAndTmIdAndRole(UUID id, UUID tmUserId, Role role) {
        return userAccountRepository.findByIdAndStatusAndRoleAndLocationManagedByTm(
                id,
                RegistrationStatus.PENDING_APPROVAL,
                role,
                tmUserId
        );
    }

    @Transactional
    public ApprovalResult approve(UserAccount request, Long approvedByTelegramUserId) {
        Optional<UserAccount> tmAccount = userAccountRepository.findByTelegramUserId(approvedByTelegramUserId);
        if (request.getRole() == Role.SELLER && request.getLocation() != null) {
            long approvedSellers = userAccountRepository.countByStatusAndRoleAndLocation_Id(
                    RegistrationStatus.APPROVED,
                    Role.SELLER,
                    request.getLocation().getId()
            );
            if (approvedSellers >= 2) {
                return rejectWithReason(
                        request,
                        approvedByTelegramUserId,
                        RegistrationStatus.REJECTED,
                        "На цій локації вже є 2 продавці. Заявку відхилено."
                );
            }
        }

        if (request.getRole() == Role.SENIOR_SELLER && tmAccount.isPresent()) {
            long approvedSeniorSellers = userAccountRepository.countByStatusAndRoleAndLocationManagedByTm(
                    RegistrationStatus.APPROVED,
                    Role.SENIOR_SELLER,
                    tmAccount.get().getId()
            );
            if (approvedSeniorSellers >= 1) {
                return rejectWithReason(
                        request,
                        approvedByTelegramUserId,
                        RegistrationStatus.BLOCKED,
                        "У локаціях ТМ вже є старший продавець. Заявку заблоковано."
                );
            }
        }

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
        return new ApprovalResult(saved, true, "Готово. Працівника додано.");
    }

    @Transactional
    public UserAccount reject(UserAccount request) {
        request.setStatus(RegistrationStatus.REJECTED);
        return userAccountRepository.save(request);
    }

    private ApprovalResult rejectWithReason(
            UserAccount request,
            Long approvedByTelegramUserId,
            RegistrationStatus status,
            String message
    ) {
        request.setStatus(status);
        request.setApprovedByTelegramUserId(approvedByTelegramUserId);
        request.setApprovedAt(Instant.now());
        UserAccount saved = userAccountRepository.save(request);
        return new ApprovalResult(saved, false, message);
    }

    public record ApprovalResult(UserAccount account, boolean approved, String message) {
    }
}
