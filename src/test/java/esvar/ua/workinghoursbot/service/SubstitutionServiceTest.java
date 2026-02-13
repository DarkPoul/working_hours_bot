package esvar.ua.workinghoursbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import esvar.ua.workinghoursbot.domain.Location;
import esvar.ua.workinghoursbot.domain.RegistrationStatus;
import esvar.ua.workinghoursbot.domain.Role;
import esvar.ua.workinghoursbot.domain.ScheduleDay;
import esvar.ua.workinghoursbot.domain.ScheduleStatus;
import esvar.ua.workinghoursbot.domain.SubstitutionCandidateState;
import esvar.ua.workinghoursbot.domain.SubstitutionRequest;
import esvar.ua.workinghoursbot.domain.SubstitutionRequestCandidate;
import esvar.ua.workinghoursbot.domain.SubstitutionRequestScope;
import esvar.ua.workinghoursbot.domain.SubstitutionRequestStatus;
import esvar.ua.workinghoursbot.domain.UserAccount;
import esvar.ua.workinghoursbot.repository.LocationRepository;
import esvar.ua.workinghoursbot.repository.ScheduleDayRepository;
import esvar.ua.workinghoursbot.repository.SubstitutionRequestCandidateRepository;
import esvar.ua.workinghoursbot.repository.SubstitutionRequestRepository;
import esvar.ua.workinghoursbot.repository.UserAccountRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@SpringBootTest
class SubstitutionServiceTest {

    @Autowired
    private SubstitutionService substitutionService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private ScheduleDayRepository scheduleDayRepository;

    @Autowired
    private SubstitutionRequestRepository substitutionRequestRepository;

    @Autowired
    private SubstitutionRequestCandidateRepository candidateRepository;

    @Autowired
    private SubstitutionInteractionHandler substitutionInteractionHandler;

    @Autowired
    private SubstitutionNotificationService substitutionNotificationService;

    @Test
    void findCandidatesFiltersWorkingUsers() {
        Location location = createLocation("L1");
        UserAccount requester = createUser("Requester", 100L, location, Role.SELLER);
        UserAccount candidateFree = createUser("Free", 200L, location, Role.SELLER);
        UserAccount candidateBusy = createUser("Busy", 300L, location, Role.SELLER);

        LocalDate date = LocalDate.now().plusDays(1);
        createWorkDay(requester.getTelegramUserId(), location.getId(), date);
        createWorkDay(candidateBusy.getTelegramUserId(), location.getId(), date);

        SubstitutionRequest request = createRequest(requester, location, date, SubstitutionRequestStatus.IN_PROGRESS);
        request.setScope(SubstitutionRequestScope.LOCATION);
        substitutionRequestRepository.save(request);

        List<UserAccount> candidates = substitutionService.findCandidates(
                request.getId(),
                SubstitutionRequestScope.LOCATION,
                null
        );

        assertThat(candidates)
                .extracting(UserAccount::getId)
                .containsExactly(candidateFree.getId());
    }

    @Test
    void acceptOfferAllowsOnlyOneCandidate() {
        Location location = createLocation("L2");
        UserAccount requester = createUser("Requester2", 400L, location, Role.SELLER);
        UserAccount candidateOne = createUser("Candidate1", 500L, location, Role.SELLER);
        UserAccount candidateTwo = createUser("Candidate2", 600L, location, Role.SELLER);

        LocalDate date = LocalDate.now().plusDays(2);
        createWorkDay(requester.getTelegramUserId(), location.getId(), date);

        SubstitutionRequest request = createRequest(requester, location, date, SubstitutionRequestStatus.IN_PROGRESS);
        request.setScope(SubstitutionRequestScope.LOCATION);
        substitutionRequestRepository.save(request);

        createCandidate(request, candidateOne);
        createCandidate(request, candidateTwo);

        SubstitutionService.AcceptOfferResult firstResult = substitutionService.acceptOffer(
                request.getId(),
                candidateOne.getTelegramUserId()
        );
        SubstitutionService.AcceptOfferResult secondResult = substitutionService.acceptOffer(
                request.getId(),
                candidateTwo.getTelegramUserId()
        );

        SubstitutionRequest updated = substitutionRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SubstitutionRequestStatus.WAITING_TM_APPROVAL);
        assertThat(updated.getProposedReplacementUser().getId())
                .isIn(candidateOne.getId(), candidateTwo.getId());

        List<SubstitutionRequestCandidate> candidates = candidateRepository.findByRequest_IdAndStateIn(
                request.getId(),
                List.of(SubstitutionCandidateState.ACCEPTED, SubstitutionCandidateState.EXPIRED)
        );
        long acceptedCount = candidates.stream()
                .filter(candidate -> candidate.getState() == SubstitutionCandidateState.ACCEPTED)
                .count();
        assertThat(acceptedCount).isEqualTo(1);

        assertThat(firstResult.getStatus()).isEqualTo(SubstitutionService.AcceptOfferResult.Status.WAITING_TM_APPROVAL);
        assertThat(secondResult.getStatus()).isEqualTo(SubstitutionService.AcceptOfferResult.Status.CLOSED);
    }

    @Test
    void findTmForRequestUsesManagedLocations() {
        Location location = createLocation("L3");
        UserAccount tm = createUser("TM Manager", 700L, null, Role.TM);
        tm.getManagedLocations().add(location);
        userAccountRepository.save(tm);

        Optional<UserAccount> resolved = substitutionService.findTmForRequest(location);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getId()).isEqualTo(tm.getId());
    }

    @Test
    void notifyTmApprovalWarnsRequesterWhenTmMissing() {
        Location location = createLocation("L4");
        UserAccount requester = createUser("Requester3", 800L, location, Role.SELLER);

        SubstitutionRequest request = createRequest(
                requester,
                location,
                LocalDate.now().plusDays(1),
                SubstitutionRequestStatus.WAITING_TM_APPROVAL
        );

        List<org.telegram.telegrambots.meta.api.methods.BotApiMethod<?>> actions =
                substitutionInteractionHandler.notifyTmApproval(request);

        assertThat(actions).hasSize(1);
        SendMessage message = (SendMessage) actions.get(0);
        assertThat(message.getText())
                .isEqualTo("⚠️ Не знайдено ТМ для підтвердження підміни. Зверніться до адміністратора.");
    }

    @Test
    void findSeniorPrefersLocationMatch() {
        Location location = createLocation("L5");
        UserAccount localSenior = createUser("Senior", 901L, location, Role.SENIOR_SELLER);
        createUser("Other Senior", 902L, null, Role.SENIOR_SELLER);

        SubstitutionService.SeniorLookupResult result = substitutionService.findSeniorForRequestWithDiagnostics(
                location,
                location.getTmUserId()
        );

        assertThat(result.senior()).isPresent();
        assertThat(result.senior().get().getId()).isEqualTo(localSenior.getId());
        assertThat(result.stage()).isEqualTo(SubstitutionService.SeniorLookupStage.LOCATION);
    }

    @Test
    void findSeniorFallsBackToTmApprover() {
        Long tmTelegramUserId = 777L;
        Location location = createLocation("L6", tmTelegramUserId);
        UserAccount tmApprovedSenior = createUser("Senior2", 903L, null, Role.SENIOR_SELLER);
        tmApprovedSenior.setApprovedByTelegramUserId(tmTelegramUserId);
        userAccountRepository.save(tmApprovedSenior);

        SubstitutionService.SeniorLookupResult result = substitutionService.findSeniorForRequestWithDiagnostics(
                location,
                tmTelegramUserId
        );

        assertThat(result.senior()).isPresent();
        assertThat(result.senior().get().getId()).isEqualTo(tmApprovedSenior.getId());
        assertThat(result.stage()).isEqualTo(SubstitutionService.SeniorLookupStage.TM_APPROVER);
    }

    @Test
    void findSeniorReturnsEmptyWhenNoneFound() {
        candidateRepository.deleteAll();
        substitutionRequestRepository.deleteAll();
        scheduleDayRepository.deleteAll();
        userAccountRepository.deleteAll();
        locationRepository.deleteAll();

        Location location = createLocation("L7", 888L);

        SubstitutionService.SeniorLookupResult result = substitutionService.findSeniorForRequestWithDiagnostics(
                location,
                location.getTmUserId()
        );

        assertThat(result.senior()).isEmpty();
        assertThat(result.stage()).isEqualTo(SubstitutionService.SeniorLookupStage.NONE);
    }

    @Test
    void notifySeniorReturnsRequesterWarningWhenMissing() {
        candidateRepository.deleteAll();
        substitutionRequestRepository.deleteAll();
        scheduleDayRepository.deleteAll();
        userAccountRepository.deleteAll();
        locationRepository.deleteAll();

        Location location = createLocation("L8");
        UserAccount requester = createUser("Requester4", 1000L, location, Role.SELLER);
        SubstitutionRequest request = createRequest(
                requester,
                location,
                LocalDate.now().plusDays(1),
                SubstitutionRequestStatus.NEW
        );

        List<org.telegram.telegrambots.meta.api.methods.BotApiMethod<?>> actions =
                substitutionNotificationService.notifySeniorAboutRequest(request);

        assertThat(actions).hasSize(1);
        SendMessage message = (SendMessage) actions.getFirst();
        assertThat(message.getText())
                .isEqualTo("⚠️ Не знайдено ТМ для вашої локації. Зверніться до адміністратора.");
    }

    private Location createLocation(String name) {
        return createLocation(name, null);
    }

    private Location createLocation(String name, Long tmUserId) {
        Location location = new Location();
        location.setId(UUID.randomUUID());
        location.setCode(name);
        location.setName(name);
        location.setTmUserId(tmUserId);
        return locationRepository.save(location);
    }

    private UserAccount createUser(String name, Long telegramUserId, Location location, Role role) {
        UserAccount account = new UserAccount();
        account.setTelegramUserId(telegramUserId);
        account.setTelegramChatId(telegramUserId);
        account.setLastName(name);
        account.setRole(role);
        account.setLocation(location);
        account.setStatus(RegistrationStatus.APPROVED);
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        return userAccountRepository.save(account);
    }

    private void createWorkDay(Long telegramUserId, UUID locationId, LocalDate date) {
        ScheduleDay day = new ScheduleDay();
        day.setTelegramUserId(telegramUserId);
        day.setLocationId(locationId);
        day.setDate(date);
        day.setStatus(ScheduleStatus.WORK);
        scheduleDayRepository.save(day);
    }

    private SubstitutionRequest createRequest(UserAccount requester,
                                              Location location,
                                              LocalDate date,
                                              SubstitutionRequestStatus status) {
        SubstitutionRequest request = new SubstitutionRequest();
        request.setId(UUID.randomUUID());
        request.setRequester(requester);
        request.setLocation(location);
        request.setRequestDate(date);
        request.setStatus(status);
        request.setUrgent(false);
        request.setCreatedAt(Instant.now());
        return request;
    }

    private void createCandidate(SubstitutionRequest request, UserAccount candidate) {
        SubstitutionRequestCandidate entry = new SubstitutionRequestCandidate();
        entry.setRequest(request);
        entry.setCandidate(candidate);
        entry.setState(SubstitutionCandidateState.NOTIFIED);
        entry.setNotifiedChatId(candidate.getTelegramChatId());
        candidateRepository.save(entry);
    }
}
