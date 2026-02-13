package esvar.ua.workinghoursbot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import esvar.ua.workinghoursbot.bot.BotResponse;
import esvar.ua.workinghoursbot.domain.RegistrationSession;
import esvar.ua.workinghoursbot.domain.RegistrationState;
import esvar.ua.workinghoursbot.domain.UserAccount;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private UserAccountService userAccountService;
    @Mock
    private RegistrationSessionService registrationSessionService;
    @Mock
    private LocationService locationService;
    @Mock
    private MainMenuService mainMenuService;
    @Mock
    private AuditService auditService;

    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new RegistrationService(
                userAccountService,
                registrationSessionService,
                locationService,
                mainMenuService,
                auditService
        );
    }

    @Test
    void chooseRoleRejectsSeniorSellerOption() {
        long telegramUserId = 10L;
        long chatId = 20L;

        RegistrationSession session = new RegistrationSession();
        session.setTelegramUserId(telegramUserId);
        session.setState(RegistrationState.CHOOSE_ROLE);
        session.setDraftLastName("Тест Користувач");

        when(userAccountService.findByTelegramUserId(telegramUserId)).thenReturn(Optional.empty());
        when(registrationSessionService.findByTelegramUserId(telegramUserId)).thenReturn(Optional.of(session));

        BotResponse response = registrationService.handleText(telegramUserId, chatId, "Старший продавець");

        SendMessage message = assertInstanceOf(SendMessage.class, response.actions().get(0));
        assertEquals("Будь ласка, оберіть роль кнопками.", message.getText());
        verify(registrationSessionService, never()).save(session);
    }

    @Test
    void chooseRoleAcceptsSellerOption() {
        long telegramUserId = 11L;
        long chatId = 21L;

        RegistrationSession session = new RegistrationSession();
        session.setTelegramUserId(telegramUserId);
        session.setState(RegistrationState.CHOOSE_ROLE);
        session.setDraftLastName("Тест Користувач");

        when(userAccountService.findByTelegramUserId(telegramUserId)).thenReturn(Optional.empty());
        when(registrationSessionService.findByTelegramUserId(telegramUserId)).thenReturn(Optional.of(session));
        when(locationService.findActivePage(0, 8)).thenReturn(Page.empty());

        registrationService.handleText(telegramUserId, chatId, "Продавець");

        assertEquals(RegistrationState.CHOOSE_LOCATION, session.getState());
        verify(registrationSessionService).save(session);
    }
}
