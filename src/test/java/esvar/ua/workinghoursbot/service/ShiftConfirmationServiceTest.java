package esvar.ua.workinghoursbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import esvar.ua.workinghoursbot.domain.Location;
import esvar.ua.workinghoursbot.domain.RegistrationStatus;
import esvar.ua.workinghoursbot.domain.ShiftConfirmation;
import esvar.ua.workinghoursbot.domain.ShiftConfirmationStatus;
import esvar.ua.workinghoursbot.domain.SubstitutionRequest;
import esvar.ua.workinghoursbot.domain.UserAccount;
import esvar.ua.workinghoursbot.repository.ScheduleDayRepository;
import esvar.ua.workinghoursbot.repository.ShiftConfirmationRepository;
import esvar.ua.workinghoursbot.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;

@ExtendWith(MockitoExtension.class)
class ShiftConfirmationServiceTest {

    @Mock
    private ShiftConfirmationRepository confirmationRepository;

    @Mock
    private ScheduleDayRepository scheduleDayRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private SubstitutionService substitutionService;

    @Mock
    private SubstitutionNotificationService substitutionNotificationService;

    @Mock
    private TelegramSender telegramSender;

    @Mock
    private AuditService auditService;

    @Mock
    private MainMenuService mainMenuService;

    private Clock clock;

    private ShiftConfirmationService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2024-01-01T09:00:00Z"), ZoneId.of("Europe/Kiev"));
        service = new ShiftConfirmationService(
                confirmationRepository,
                scheduleDayRepository,
                userAccountRepository,
                substitutionService,
                substitutionNotificationService,
                telegramSender,
                auditService,
                mainMenuService,
                clock
        );
    }

    @Test
    void handleYesConfirmationUpdatesStatus() {
        ShiftConfirmation confirmation = buildConfirmation(ShiftConfirmationStatus.PENDING);
        when(confirmationRepository.findFirstByTelegramUserIdAndStatusOrderByAskedAtDesc(
                confirmation.getTelegramUserId(),
                ShiftConfirmationStatus.PENDING
        )).thenReturn(Optional.of(confirmation));

        UserAccount account = buildAccount();
        when(userAccountRepository.findByTelegramUserId(confirmation.getTelegramUserId()))
                .thenReturn(Optional.of(account));
        when(mainMenuService.mainMenuKeyboard(anyLong())).thenReturn(new ReplyKeyboardMarkup());

        service.handleResponse(confirmation.getTelegramUserId(), account.getTelegramChatId(), "Так");

        ArgumentCaptor<ShiftConfirmation> captor = ArgumentCaptor.forClass(ShiftConfirmation.class);
        verify(confirmationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ShiftConfirmationStatus.YES);
        verify(substitutionService, never()).createRequest(anyLong(), any(), anyBoolean(), any(), any());
    }

    @Test
    void handleNoConfirmationCreatesSubstitution() {
        ShiftConfirmation confirmation = buildConfirmation(ShiftConfirmationStatus.PENDING);
        when(confirmationRepository.findFirstByTelegramUserIdAndStatusOrderByAskedAtDesc(
                confirmation.getTelegramUserId(),
                ShiftConfirmationStatus.PENDING
        )).thenReturn(Optional.of(confirmation));

        UserAccount account = buildAccount();
        when(userAccountRepository.findByTelegramUserId(confirmation.getTelegramUserId()))
                .thenReturn(Optional.of(account));
        when(mainMenuService.mainMenuKeyboard(anyLong())).thenReturn(new ReplyKeyboardMarkup());
        when(substitutionService.createRequest(anyLong(), any(), anyBoolean(), any(), any()))
                .thenReturn(new SubstitutionRequest());
        when(substitutionNotificationService.notifySeniorAboutRequest(any(SubstitutionRequest.class)))
                .thenReturn(List.of());
        when(substitutionService.findSeniorForRequest(account.getLocation())).thenReturn(Optional.empty());

        service.handleResponse(confirmation.getTelegramUserId(), account.getTelegramChatId(), "Ні");

        ArgumentCaptor<ShiftConfirmation> captor = ArgumentCaptor.forClass(ShiftConfirmation.class);
        verify(confirmationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ShiftConfirmationStatus.NO);
        verify(substitutionNotificationService).notifySeniorAboutRequest(any(SubstitutionRequest.class));
    }

    @Test
    void autoRejectPendingMarksNoAndCreatesSubstitution() {
        ShiftConfirmation confirmation = buildConfirmation(ShiftConfirmationStatus.PENDING);
        UserAccount account = buildAccount();
        when(confirmationRepository.findByStatus(ShiftConfirmationStatus.PENDING))
                .thenReturn(List.of(confirmation));
        when(userAccountRepository.findByTelegramUserId(confirmation.getTelegramUserId()))
                .thenReturn(Optional.of(account));
        when(substitutionService.createRequest(anyLong(), any(), anyBoolean(), any(), any()))
                .thenReturn(new SubstitutionRequest());
        when(substitutionNotificationService.notifySeniorAboutRequest(any(SubstitutionRequest.class)))
                .thenReturn(List.of());
        when(substitutionService.findSeniorForRequest(account.getLocation())).thenReturn(Optional.empty());

        service.autoRejectPending();

        ArgumentCaptor<ShiftConfirmation> captor = ArgumentCaptor.forClass(ShiftConfirmation.class);
        verify(confirmationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ShiftConfirmationStatus.NO);
        assertThat(captor.getValue().isAutoRejected()).isTrue();
        verify(substitutionNotificationService).notifySeniorAboutRequest(any(SubstitutionRequest.class));
    }

    private ShiftConfirmation buildConfirmation(ShiftConfirmationStatus status) {
        ShiftConfirmation confirmation = new ShiftConfirmation();
        confirmation.setId(UUID.randomUUID());
        confirmation.setTelegramUserId(11L);
        confirmation.setLocationId(UUID.randomUUID());
        confirmation.setDate(LocalDate.of(2024, 1, 2));
        confirmation.setStatus(status);
        confirmation.setAskedAt(Instant.now(clock));
        return confirmation;
    }

    private UserAccount buildAccount() {
        Location location = new Location();
        location.setId(UUID.randomUUID());
        location.setName("Локація 1");
        UserAccount account = new UserAccount();
        account.setId(UUID.randomUUID());
        account.setTelegramUserId(11L);
        account.setTelegramChatId(11L);
        account.setLastName("Тест");
        account.setStatus(RegistrationStatus.APPROVED);
        account.setLocation(location);
        return account;
    }
}
