package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.domain.AuditEventType;
import esvar.ua.workinghoursbot.domain.Location;
import esvar.ua.workinghoursbot.domain.RegistrationStatus;
import esvar.ua.workinghoursbot.domain.Role;
import esvar.ua.workinghoursbot.domain.ScheduleStatus;
import esvar.ua.workinghoursbot.domain.SubstitutionCandidateState;
import esvar.ua.workinghoursbot.domain.SubstitutionRequest;
import esvar.ua.workinghoursbot.domain.SubstitutionRequestCandidate;
import esvar.ua.workinghoursbot.domain.SubstitutionRequestScope;
import esvar.ua.workinghoursbot.domain.SubstitutionRequestStatus;
import esvar.ua.workinghoursbot.domain.UserAccount;
import esvar.ua.workinghoursbot.repository.ScheduleDayRepository;
import esvar.ua.workinghoursbot.repository.SubstitutionRequestCandidateRepository;
import esvar.ua.workinghoursbot.repository.SubstitutionRequestRepository;
import esvar.ua.workinghoursbot.repository.UserAccountRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubstitutionService {

    private static final Set<Role> CANDIDATE_ROLES = EnumSet.of(Role.SELLER, Role.SENIOR_SELLER);
    private static final Set<SubstitutionRequestStatus> ACTIVE_STATUSES = EnumSet.of(
            SubstitutionRequestStatus.NEW,
            SubstitutionRequestStatus.IN_PROGRESS,
            SubstitutionRequestStatus.WAITING_TM_APPROVAL
    );
    private static final java.time.format.DateTimeFormatter DATE_FORMAT = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final SubstitutionRequestRepository substitutionRequestRepository;
    private final SubstitutionRequestCandidateRepository candidateRepository;
    private final UserAccountRepository userAccountRepository;
    private final ScheduleDayRepository scheduleDayRepository;
    private final SchedulePersistenceService schedulePersistenceService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<LocalDate> getPlannedWorkDates(Long telegramUserId) {
        Optional<UserAccount> accountOptional = userAccountRepository.findByTelegramUserId(telegramUserId);
        if (accountOptional.isEmpty()) {
            return List.of();
        }
        UserAccount account = accountOptional.get();
        Location location = account.getLocation();
        if (location == null) {
            return List.of();
        }
        YearMonth current = YearMonth.now();
        YearMonth next = current.plusMonths(1);
        Set<LocalDate> currentDates = schedulePersistenceService.loadMonth(
                telegramUserId,
                location.getId(),
                current
        );
        Set<LocalDate> nextDates = schedulePersistenceService.loadMonth(
                telegramUserId,
                location.getId(),
                next
        );
        List<LocalDate> combined = new ArrayList<>();
        combined.addAll(currentDates);
        combined.addAll(nextDates);
        return combined.stream().distinct().sorted().toList();
    }

    @Transactional(readOnly = true)
    public boolean isWorking(Long telegramUserId, LocalDate date) {
        return scheduleDayRepository.existsByTelegramUserIdAndDateAndStatus(
                telegramUserId,
                date,
                ScheduleStatus.WORK
        );
    }

    @Transactional
    public SubstitutionRequest createRequest(Long telegramUserId,
                                             LocalDate date,
                                             boolean urgent,
                                             UUID requestId,
                                             String reason) {
        UserAccount requester = userAccountRepository.findByTelegramUserId(telegramUserId)
                .orElseThrow(() -> new IllegalStateException("Користувача не знайдено."));
        if (requester.getLocation() == null) {
            throw new IllegalStateException("Спочатку оберіть локацію.");
        }
        if (date.isBefore(LocalDate.now())) {
            throw new IllegalStateException("Не можна створити підміну на минулу дату.");
        }
        if (!isWorking(telegramUserId, date)) {
            throw new IllegalStateException("У цей день у вас немає робочої зміни.");
        }
        boolean existsActive = substitutionRequestRepository.existsByRequester_IdAndRequestDateAndStatusIn(
                requester.getId(),
                date,
                ACTIVE_STATUSES
        );
        if (existsActive) {
            throw new IllegalStateException("Вже є активний запит на цю дату.");
        }

        SubstitutionRequest request = new SubstitutionRequest();
        request.setId(requestId);
        request.setRequester(requester);
        request.setLocation(requester.getLocation());
        request.setRequestDate(date);
        request.setStatus(SubstitutionRequestStatus.NEW);
        request.setUrgent(urgent);
        request.setCreatedAt(Instant.now());
        SubstitutionRequest saved = substitutionRequestRepository.save(request);

        log.info("Created substitution request. requestId={}, requesterId={}, date={}, urgent={}",
                saved.getId(), requester.getId(), date, urgent);
        auditService.log(
                AuditEventType.SWAP_REQUEST_CREATED,
                requester.getId(),
                null,
                requester.getLocation().getId(),
                "%s %s | Локація: %s | Причина: %s".formatted(
                        DATE_FORMAT.format(date),
                        requester.getLastName(),
                        requester.getLocation().getName(),
                        reason == null ? "-" : reason
                )
        );
        return saved;
    }

    @Transactional
    public SubstitutionRequest approveBySenior(UUID requestId, Long seniorTelegramUserId) {
        SubstitutionRequest request = substitutionRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Запит не знайдено."));
        if (!ACTIVE_STATUSES.contains(request.getStatus())) {
            throw new IllegalStateException("Запит вже оброблено.");
        }
        UserAccount senior = userAccountRepository.findByTelegramUserId(seniorTelegramUserId)
                .orElseThrow(() -> new IllegalStateException("Користувача не знайдено."));

        request.setStatus(SubstitutionRequestStatus.WAITING_TM_APPROVAL);
        request.setProposedReplacementUser(senior);
        SubstitutionRequest saved = substitutionRequestRepository.save(request);

        log.info("Request approved by senior. requestId={}, seniorId={}", requestId, senior.getId());
        return saved;
    }

    @Transactional
    public SubstitutionRequest rejectRequest(UUID requestId, Long seniorTelegramUserId, String reason) {
        SubstitutionRequest request = substitutionRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Запит не знайдено."));
        if (!ACTIVE_STATUSES.contains(request.getStatus())) {
            throw new IllegalStateException("Запит вже оброблено.");
        }
        UserAccount senior = userAccountRepository.findByTelegramUserId(seniorTelegramUserId)
                .orElseThrow(() -> new IllegalStateException("Користувача не знайдено."));

        request.setStatus(SubstitutionRequestStatus.REJECTED);
        request.setRejectReason(reason);
        request.setResolvedByUser(senior);
        request.setResolvedAt(Instant.now());
        SubstitutionRequest saved = substitutionRequestRepository.save(request);

        markOtherCandidatesExpired(request.getId());

        log.info("Request rejected by senior. requestId={}, seniorId={}, reason={}", requestId, senior.getId(), reason);
        return saved;
    }

    @Transactional
    public SubstitutionRequest setScope(UUID requestId, SubstitutionRequestScope scope) {
        SubstitutionRequest request = substitutionRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Запит не знайдено."));
        if (request.getStatus() == SubstitutionRequestStatus.APPROVED
                || request.getStatus() == SubstitutionRequestStatus.REJECTED
                || request.getStatus() == SubstitutionRequestStatus.WAITING_TM_APPROVAL
                || request.getStatus() == SubstitutionRequestStatus.CANCELLED) {
            throw new IllegalStateException("Запит вже закрито.");
        }
        request.setScope(scope);
        if (request.getStatus() == SubstitutionRequestStatus.NEW) {
            request.setStatus(SubstitutionRequestStatus.IN_PROGRESS);
        }
        SubstitutionRequest saved = substitutionRequestRepository.save(request);
        log.info("Scope set for request. requestId={}, scope={}", requestId, scope);
        return saved;
    }

    @Transactional(readOnly = true)
    public SubstitutionRequest getRequest(UUID requestId) {
        return substitutionRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Запит не знайдено."));
    }

    @Transactional(readOnly = true)
    public List<UserAccount> findCandidates(UUID requestId, SubstitutionRequestScope scope, UserAccount senior) {
        SubstitutionRequest request = substitutionRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Запит не знайдено."));
        List<UserAccount> pool;
        if (scope == SubstitutionRequestScope.LOCATION) {
            pool = userAccountRepository.findByStatusAndRoleInAndLocation_Id(
                    RegistrationStatus.APPROVED,
                    List.copyOf(CANDIDATE_ROLES),
                    request.getLocation().getId()
            );
        } else if (scope == SubstitutionRequestScope.TM) {
            Optional<UserAccount> tmOptional = userAccountRepository.findActiveTmByManagedLocation(
                    request.getLocation().getId()
            );
            if (tmOptional.isEmpty()) {
                pool = userAccountRepository.findByStatusAndRoleInAndLocation_Id(
                        RegistrationStatus.APPROVED,
                        List.copyOf(CANDIDATE_ROLES),
                        request.getLocation().getId()
                );
            } else {
                pool = userAccountRepository.findByStatusAndRoleInAndLocationManagedByTm(
                        RegistrationStatus.APPROVED,
                        List.copyOf(CANDIDATE_ROLES),
                        tmOptional.get().getId()
                );
            }
        } else {
            pool = userAccountRepository.findByStatusAndRoleIn(
                    RegistrationStatus.APPROVED,
                    List.copyOf(CANDIDATE_ROLES)
            );
        }

        LocalDate requestDate = request.getRequestDate();
        UUID requesterId = request.getRequester().getId();
        return pool.stream()
                .filter(user -> !user.getId().equals(requesterId))
                .filter(user -> !isWorking(user.getTelegramUserId(), requestDate))
                .sorted(Comparator.comparing(UserAccount::getLastName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    @Transactional
    public List<UserAccount> notifyAllCandidates(UUID requestId, SubstitutionRequestScope scope, UserAccount senior) {
        SubstitutionRequest request = substitutionRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Запит не знайдено."));
        if (request.getStatus() != SubstitutionRequestStatus.IN_PROGRESS) {
            throw new IllegalStateException("Запит не в роботі.");
        }
        List<UserAccount> candidates = findCandidates(requestId, scope, senior);
        candidates.forEach(candidate -> upsertCandidate(request, candidate, SubstitutionCandidateState.NOTIFIED));
        log.info("Notified all candidates. requestId={}, count={}", requestId, candidates.size());
        return candidates;
    }

    @Transactional
    public UserAccount notifySingleCandidate(UUID requestId,
                                             UUID candidateId,
                                             SubstitutionRequestScope scope,
                                             UserAccount senior) {
        List<UserAccount> candidates = findCandidates(requestId, scope, senior);
        SubstitutionRequest request = substitutionRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Запит не знайдено."));
        if (request.getStatus() != SubstitutionRequestStatus.IN_PROGRESS) {
            throw new IllegalStateException("Запит не в роботі.");
        }
        UserAccount candidate = candidates.stream()
                .filter(user -> user.getId().equals(candidateId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Кандидат недоступний."));
        upsertCandidate(request, candidate, SubstitutionCandidateState.NOTIFIED);
        log.info("Notified single candidate. requestId={}, candidateId={}", requestId, candidateId);
        return candidate;
    }

    @Transactional
    public AcceptOfferResult acceptOffer(UUID requestId, Long candidateTelegramUserId) {
        UserAccount candidate = userAccountRepository.findByTelegramUserId(candidateTelegramUserId)
                .orElseThrow(() -> new IllegalStateException("Користувача не знайдено."));

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                SubstitutionRequest request = substitutionRequestRepository.findWithLockById(requestId)
                        .orElseThrow(() -> new IllegalStateException("Запит не знайдено."));

                if (request.getStatus() == SubstitutionRequestStatus.APPROVED) {
                    return AcceptOfferResult.alreadyApproved(request, candidate);
                }
                if (request.getStatus() == SubstitutionRequestStatus.REJECTED
                        || request.getStatus() == SubstitutionRequestStatus.WAITING_TM_APPROVAL
                        || request.getStatus() == SubstitutionRequestStatus.CANCELLED) {
                    return AcceptOfferResult.closed(request, candidate);
                }

                request.setStatus(SubstitutionRequestStatus.WAITING_TM_APPROVAL);
                request.setProposedReplacementUser(candidate);
                substitutionRequestRepository.save(request);

                SubstitutionRequestCandidate candidateEntry = candidateRepository
                        .findByRequest_IdAndCandidate_Id(requestId, candidate.getId())
                        .orElseGet(() -> {
                            SubstitutionRequestCandidate entry = new SubstitutionRequestCandidate();
                            entry.setRequest(request);
                            entry.setCandidate(candidate);
                            entry.setState(SubstitutionCandidateState.NOTIFIED);
                            return entry;
                        });
                candidateEntry.setState(SubstitutionCandidateState.ACCEPTED);
                candidateEntry.setNotifiedChatId(candidate.getTelegramChatId());
                candidateRepository.save(candidateEntry);

                log.info("Offer accepted. requestId={}, candidateId={}", requestId, candidate.getId());
                auditService.log(
                        AuditEventType.SWAP_CANDIDATE_SELECTED,
                        candidate.getId(),
                        request.getRequester().getId(),
                        request.getLocation().getId(),
                        "%s %s | Локація: %s | Кандидат: %s".formatted(
                                DATE_FORMAT.format(request.getRequestDate()),
                                request.getRequester().getLastName(),
                                request.getLocation().getName(),
                                candidate.getLastName()
                        )
                );
                return AcceptOfferResult.approved(request, candidate, List.of());
            } catch (OptimisticLockingFailureException | CannotAcquireLockException ex) {
                log.warn("Lock conflict on accept offer. requestId={}, candidateId={}, attempt={}",
                        requestId, candidate.getId(), attempt + 1);
                if (attempt == 1) {
                    SubstitutionRequest request = substitutionRequestRepository.findById(requestId)
                            .orElseThrow(() -> new IllegalStateException("Запит не знайдено."));
                    return AcceptOfferResult.alreadyApproved(request, candidate);
                }
            }
        }
        SubstitutionRequest request = substitutionRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Запит не знайдено."));
        return AcceptOfferResult.alreadyApproved(request, candidate);
    }

    @Transactional(readOnly = true)
    public List<SubstitutionRequest> listActiveRequestsForSenior(UserAccount senior) {
        List<SubstitutionRequestStatus> statuses = List.of(
                SubstitutionRequestStatus.NEW,
                SubstitutionRequestStatus.IN_PROGRESS,
                SubstitutionRequestStatus.WAITING_TM_APPROVAL
        );
        if (senior.getLocation() == null) {
            return substitutionRequestRepository.findByStatusInOrderByRequestDateAsc(statuses);
        }
        Optional<UserAccount> tmOptional = userAccountRepository.findActiveTmByManagedLocation(
                senior.getLocation().getId()
        );
        if (tmOptional.isEmpty()) {
            return substitutionRequestRepository.findByStatusInAndLocation_IdOrderByRequestDateAsc(
                    statuses,
                    senior.getLocation().getId()
            );
        }
        return substitutionRequestRepository.findByStatusInAndLocationManagedByTmOrderByRequestDateAsc(
                statuses,
                tmOptional.get().getId()
        );
    }

    @Transactional
    public SubstitutionRequest submitToTmApproval(UUID requestId, UUID actorUserId) {
        SubstitutionRequest request = substitutionRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Запит не знайдено."));
        if (request.getStatus() != SubstitutionRequestStatus.WAITING_TM_APPROVAL) {
            throw new IllegalStateException("Запит не потребує підтвердження ТМ.");
        }
        Optional<UserAccount> tmOptional = findTmForRequest(request.getLocation());
        tmOptional.ifPresent(request::setTmUser);
        SubstitutionRequest saved = substitutionRequestRepository.save(request);
        auditService.log(
                AuditEventType.SWAP_SENT_TO_TM,
                actorUserId,
                request.getRequester().getId(),
                request.getLocation().getId(),
                "%s %s | Локація: %s".formatted(
                        DATE_FORMAT.format(request.getRequestDate()),
                        request.getRequester().getLastName(),
                        request.getLocation().getName()
                )
        );
        return saved;
    }

    @Transactional
    public SubstitutionRequest tmApprove(UUID requestId, Long tmTelegramUserId) {
        UserAccount tmUser = userAccountRepository.findByTelegramUserId(tmTelegramUserId)
                .orElseThrow(() -> new IllegalStateException("Користувача не знайдено."));
        SubstitutionRequest request = substitutionRequestRepository.findWithLockById(requestId)
                .orElseThrow(() -> new IllegalStateException("Запит не знайдено."));
        if (request.getStatus() != SubstitutionRequestStatus.WAITING_TM_APPROVAL) {
            throw new IllegalStateException("Запит вже закритий.");
        }
        UserAccount proposed = request.getProposedReplacementUser();
        if (proposed == null) {
            throw new IllegalStateException("Кандидат не знайдений.");
        }
        request.setStatus(SubstitutionRequestStatus.APPROVED);
        request.setReplacementUser(proposed);
        request.setResolvedByUser(tmUser);
        request.setResolvedAt(Instant.now());
        request.setTmUser(tmUser);
        request.setTmDecision("APPROVE");
        request.setTmDecidedAt(Instant.now());
        SubstitutionRequest saved = substitutionRequestRepository.save(request);

        schedulePersistenceService.applyReplacement(
                request.getRequester().getTelegramUserId(),
                proposed.getTelegramUserId(),
                request.getLocation().getId(),
                request.getRequestDate()
        );

        markOtherCandidatesExpired(requestId);

        log.info("Request approved by TM. requestId={}, tmId={}", requestId, tmUser.getId());
        auditService.log(
                AuditEventType.SWAP_TM_APPROVED,
                tmUser.getId(),
                request.getRequester().getId(),
                request.getLocation().getId(),
                "%s %s | Локація: %s".formatted(
                        DATE_FORMAT.format(request.getRequestDate()),
                        request.getRequester().getLastName(),
                        request.getLocation().getName()
                )
        );
        return saved;
    }

    @Transactional
    public SubstitutionRequest tmReject(UUID requestId, Long tmTelegramUserId) {
        UserAccount tmUser = userAccountRepository.findByTelegramUserId(tmTelegramUserId)
                .orElseThrow(() -> new IllegalStateException("Користувача не знайдено."));
        SubstitutionRequest request = substitutionRequestRepository.findWithLockById(requestId)
                .orElseThrow(() -> new IllegalStateException("Запит не знайдено."));
        if (request.getStatus() != SubstitutionRequestStatus.WAITING_TM_APPROVAL) {
            throw new IllegalStateException("Запит вже закритий.");
        }
        request.setStatus(SubstitutionRequestStatus.IN_PROGRESS);
        request.setTmUser(tmUser);
        request.setTmDecision("REJECT");
        request.setTmDecidedAt(Instant.now());
        request.setProposedReplacementUser(null);
        SubstitutionRequest saved = substitutionRequestRepository.save(request);
        log.info("Request rejected by TM. requestId={}, tmId={}", requestId, tmUser.getId());
        auditService.log(
                AuditEventType.SWAP_TM_REJECTED,
                tmUser.getId(),
                request.getRequester().getId(),
                request.getLocation().getId(),
                "%s %s | Локація: %s".formatted(
                        DATE_FORMAT.format(request.getRequestDate()),
                        request.getRequester().getLastName(),
                        request.getLocation().getName()
                )
        );
        return saved;
    }

    @Transactional
    public SubstitutionRequest cancelByStayWorking(UUID requestId, Long seniorTelegramUserId) {
        UserAccount senior = userAccountRepository.findByTelegramUserId(seniorTelegramUserId)
                .orElseThrow(() -> new IllegalStateException("Користувача не знайдено."));
        SubstitutionRequest request = substitutionRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Запит не знайдено."));
        if (request.getStatus() != SubstitutionRequestStatus.IN_PROGRESS) {
            throw new IllegalStateException("Запит вже закритий.");
        }
        request.setStatus(SubstitutionRequestStatus.CANCELLED);
        request.setResolvedByUser(senior);
        request.setResolvedAt(Instant.now());
        request.setProposedReplacementUser(null);
        SubstitutionRequest saved = substitutionRequestRepository.save(request);
        markOtherCandidatesExpired(requestId);
        auditService.log(
                AuditEventType.SWAP_CANCELLED,
                senior.getId(),
                request.getRequester().getId(),
                request.getLocation().getId(),
                "%s %s | Локація: %s".formatted(
                        DATE_FORMAT.format(request.getRequestDate()),
                        request.getRequester().getLastName(),
                        request.getLocation().getName()
                )
        );
        return saved;
    }

    @Transactional
    public void declineOffer(UUID requestId, Long candidateTelegramUserId) {
        UserAccount candidate = userAccountRepository.findByTelegramUserId(candidateTelegramUserId)
                .orElseThrow(() -> new IllegalStateException("Користувача не знайдено."));
        SubstitutionRequestCandidate entry = candidateRepository.findByRequest_IdAndCandidate_Id(requestId, candidate.getId())
                .orElseThrow(() -> new IllegalStateException("Пропозиція не знайдена."));
        entry.setState(SubstitutionCandidateState.DECLINED);
        candidateRepository.save(entry);
        log.info("Offer declined. requestId={}, candidateId={}", requestId, candidate.getId());
    }

    @Transactional
    public void registerCandidateNotification(UUID requestId, Long chatId, Integer messageId) {
        if (chatId == null || messageId == null) {
            return;
        }
        candidateRepository.findByRequest_IdAndNotifiedChatId(requestId, chatId)
                .ifPresent(candidate -> {
                    candidate.setNotifiedMessageId(messageId.longValue());
                    candidateRepository.save(candidate);
                    log.debug("Stored notification message id. requestId={}, chatId={}, messageId={}",
                            requestId, chatId, messageId);
                });
    }

    @Transactional(readOnly = true)
    public List<UserAccount> findSeniorsForLocation(UUID locationId) {
        return userAccountRepository.findByStatusAndRoleAndLocation_Id(
                RegistrationStatus.APPROVED,
                Role.SENIOR_SELLER,
                locationId
        );
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> findSeniorForRequest(Location location) {
        if (location == null) {
            return Optional.empty();
        }
        Optional<UserAccount> tmOptional = userAccountRepository.findActiveTmByManagedLocation(location.getId());
        if (tmOptional.isPresent()) {
            Optional<UserAccount> senior = userAccountRepository
                    .findFirstByStatusAndRoleAndLocationManagedByTmOrderByCreatedAtAsc(
                            RegistrationStatus.APPROVED,
                            Role.SENIOR_SELLER,
                            tmOptional.get().getId()
                    );
            if (senior.isPresent()) {
                return senior;
            }
        }
        return userAccountRepository.findFirstByStatusAndRoleAndLocation_IdOrderByCreatedAtAsc(
                RegistrationStatus.APPROVED,
                Role.SENIOR_SELLER,
                location.getId()
        );
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> findTmForRequest(Location location) {
        if (location == null) {
            return Optional.empty();
        }
        return userAccountRepository.findActiveTmByManagedLocation(location.getId());
    }

    @Transactional(readOnly = true)
    public List<SubstitutionRequestCandidate> findNotifiedCandidates(UUID requestId) {
        return candidateRepository.findByRequest_IdAndState(requestId, SubstitutionCandidateState.NOTIFIED);
    }

    @Transactional(readOnly = true)
    public List<SubstitutionRequestCandidate> findExpiredCandidates(UUID requestId) {
        return candidateRepository.findByRequest_IdAndStateIn(
                requestId,
                List.of(SubstitutionCandidateState.EXPIRED, SubstitutionCandidateState.DECLINED)
        );
    }

    private void upsertCandidate(SubstitutionRequest request, UserAccount candidate, SubstitutionCandidateState state) {
        SubstitutionRequestCandidate entry = candidateRepository.findByRequest_IdAndCandidate_Id(
                request.getId(),
                candidate.getId()
        ).orElseGet(() -> {
            SubstitutionRequestCandidate candidateEntry = new SubstitutionRequestCandidate();
            candidateEntry.setRequest(request);
            candidateEntry.setCandidate(candidate);
            return candidateEntry;
        });
        entry.setState(state);
        entry.setNotifiedChatId(candidate.getTelegramChatId());
        candidateRepository.save(entry);
    }

    private List<SubstitutionRequestCandidate> markOtherCandidatesExpired(UUID requestId) {
        List<SubstitutionRequestCandidate> notified = candidateRepository.findByRequest_IdAndState(
                requestId,
                SubstitutionCandidateState.NOTIFIED
        );
        if (notified.isEmpty()) {
            return List.of();
        }
        for (SubstitutionRequestCandidate candidate : notified) {
            candidate.setState(SubstitutionCandidateState.EXPIRED);
        }
        candidateRepository.saveAll(notified);
        return notified;
    }

    @Getter
    public static class AcceptOfferResult {
        private final SubstitutionRequest request;
        private final UserAccount candidate;
        private final List<SubstitutionRequestCandidate> otherCandidates;
        private final Status status;

        private AcceptOfferResult(SubstitutionRequest request,
                                  UserAccount candidate,
                                  List<SubstitutionRequestCandidate> otherCandidates,
                                  Status status) {
            this.request = request;
            this.candidate = candidate;
            this.otherCandidates = otherCandidates;
            this.status = status;
        }

        public static AcceptOfferResult approved(SubstitutionRequest request,
                                                 UserAccount candidate,
                                                 List<SubstitutionRequestCandidate> otherCandidates) {
            return new AcceptOfferResult(request, candidate, otherCandidates, Status.WAITING_TM_APPROVAL);
        }

        public static AcceptOfferResult alreadyApproved(SubstitutionRequest request, UserAccount candidate) {
            return new AcceptOfferResult(request, candidate, List.of(), Status.ALREADY_APPROVED);
        }

        public static AcceptOfferResult closed(SubstitutionRequest request, UserAccount candidate) {
            return new AcceptOfferResult(request, candidate, List.of(), Status.CLOSED);
        }

        public enum Status {
            WAITING_TM_APPROVAL,
            ALREADY_APPROVED,
            CLOSED
        }
    }
}
