package esvar.ua.workinghoursbot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import esvar.ua.workinghoursbot.domain.RegistrationStatus;
import esvar.ua.workinghoursbot.domain.Role;
import esvar.ua.workinghoursbot.domain.SellerStatus;
import esvar.ua.workinghoursbot.domain.UserAccount;
import esvar.ua.workinghoursbot.repository.UserAccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistrationRequestServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private AuditService auditService;

    private RegistrationRequestService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationRequestService(userAccountRepository, auditService);
    }

    @Test
    void approveSellerSetsSellerStatusApproved() {
        Long tmTelegramUserId = 123L;

        UserAccount tm = new UserAccount();
        tm.setId(UUID.randomUUID());

        UserAccount sellerRequest = new UserAccount();
        sellerRequest.setRole(Role.SELLER);
        sellerRequest.setStatus(RegistrationStatus.PENDING_APPROVAL);

        when(userAccountRepository.findByTelegramUserId(tmTelegramUserId)).thenReturn(Optional.of(tm));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationRequestService.ApprovalResult result = service.approve(sellerRequest, tmTelegramUserId);

        assertTrue(result.approved());
        assertEquals(RegistrationStatus.APPROVED, sellerRequest.getStatus());
        assertEquals(SellerStatus.APPROVED, sellerRequest.getSellerStatus());
        verify(userAccountRepository).save(sellerRequest);
    }

    @Test
    void rejectSellerSetsSellerStatusRejected() {
        UserAccount sellerRequest = new UserAccount();
        sellerRequest.setRole(Role.SELLER);
        sellerRequest.setStatus(RegistrationStatus.PENDING_APPROVAL);

        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserAccount rejected = service.reject(sellerRequest);

        assertEquals(RegistrationStatus.REJECTED, rejected.getStatus());
        assertEquals(SellerStatus.REJECTED, rejected.getSellerStatus());
    }

    @Test
    void approveTmDoesNotSetSellerStatus() {
        Long tmTelegramUserId = 456L;

        UserAccount tm = new UserAccount();
        tm.setId(UUID.randomUUID());

        UserAccount tmRequest = new UserAccount();
        tmRequest.setRole(Role.TM);
        tmRequest.setStatus(RegistrationStatus.PENDING_APPROVAL);

        when(userAccountRepository.findByTelegramUserId(tmTelegramUserId)).thenReturn(Optional.of(tm));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationRequestService.ApprovalResult result = service.approve(tmRequest, tmTelegramUserId);

        assertTrue(result.approved());
        assertEquals(RegistrationStatus.APPROVED, tmRequest.getStatus());
        assertFalse(tmRequest.getRole() == Role.SELLER);
        assertNull(tmRequest.getSellerStatus());
    }
}
