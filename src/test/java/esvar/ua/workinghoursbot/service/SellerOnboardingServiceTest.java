package esvar.ua.workinghoursbot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import esvar.ua.workinghoursbot.config.DomainProperties;
import esvar.ua.workinghoursbot.domain.*;
import esvar.ua.workinghoursbot.repository.JoinRequestRepository;
import esvar.ua.workinghoursbot.repository.LocationRepository;
import esvar.ua.workinghoursbot.repository.UserAccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SellerOnboardingServiceTest {
    private JoinRequestRepository joinRequestRepository;
    private UserAccountRepository userAccountRepository;
    private LocationRepository locationRepository;
    private SellerOnboardingService service;

    @BeforeEach
    void setUp() {
        joinRequestRepository = mock(JoinRequestRepository.class);
        userAccountRepository = mock(UserAccountRepository.class);
        locationRepository = mock(LocationRepository.class);
        service = new SellerOnboardingService(joinRequestRepository, userAccountRepository, locationRepository,
                new DomainProperties(new DomainProperties.Tm("1234", 3), new DomainProperties.Schedule(2)));
    }

    @Test
    void cannotCreateSecondPendingRequest() {
        UserAccount seller = new UserAccount();
        seller.setId(UUID.randomUUID());
        when(joinRequestRepository.existsBySeller_IdAndStatus(any(), eq(JoinRequestStatus.PENDING))).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.createJoinRequest(seller, UUID.randomUUID()));
    }

    @Test
    void approveIsIdempotentAndRespectsCapacity() {
        UUID requestId = UUID.randomUUID();
        UUID tmId = UUID.randomUUID();
        UserAccount tm = new UserAccount(); tm.setId(tmId);
        UserAccount seller = new UserAccount(); seller.setId(UUID.randomUUID());
        Location location = new Location(); location.setId(UUID.randomUUID());

        JoinRequest pending = new JoinRequest();
        pending.setId(requestId); pending.setTm(tm); pending.setSeller(seller); pending.setLocation(location);
        pending.setStatus(JoinRequestStatus.PENDING);
        when(joinRequestRepository.findById(requestId)).thenReturn(Optional.of(pending));
        when(userAccountRepository.countByRoleAndSellerStatusAndLocation_Id(any(), any(), any())).thenReturn(1L);
        when(joinRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        JoinRequest approved = service.approve(requestId, tmId);
        assertEquals(JoinRequestStatus.APPROVED, approved.getStatus());

        when(joinRequestRepository.findById(requestId)).thenReturn(Optional.of(approved));
        JoinRequest second = service.approve(requestId, tmId);
        assertEquals(JoinRequestStatus.APPROVED, second.getStatus());

        JoinRequest pending2 = new JoinRequest();
        pending2.setId(UUID.randomUUID()); pending2.setTm(tm); pending2.setSeller(seller); pending2.setLocation(location);
        pending2.setStatus(JoinRequestStatus.PENDING);
        when(joinRequestRepository.findById(pending2.getId())).thenReturn(Optional.of(pending2));
        when(userAccountRepository.countByRoleAndSellerStatusAndLocation_Id(any(), any(), any())).thenReturn(2L);
        assertThrows(IllegalStateException.class, () -> service.approve(pending2.getId(), tmId));
    }

    @Test
    void locationWithoutTmIsRejected() {
        UserAccount seller = new UserAccount();
        seller.setId(UUID.randomUUID());
        UUID locId = UUID.randomUUID();
        Location location = new Location();
        location.setId(locId);
        location.setTmUserId(null);

        when(joinRequestRepository.existsBySeller_IdAndStatus(any(), any())).thenReturn(false);
        when(locationRepository.findById(locId)).thenReturn(Optional.of(location));

        assertThrows(IllegalStateException.class, () -> service.createJoinRequest(seller, locId));
    }
}
