package esvar.ua.workinghoursbot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import esvar.ua.workinghoursbot.bot.BotResponse;
import esvar.ua.workinghoursbot.domain.Location;
import esvar.ua.workinghoursbot.domain.RegistrationStatus;
import esvar.ua.workinghoursbot.domain.Role;
import esvar.ua.workinghoursbot.domain.TmSession;
import esvar.ua.workinghoursbot.domain.TmState;
import esvar.ua.workinghoursbot.domain.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@ExtendWith(MockitoExtension.class)
class TmMenuServiceTest {

    @Mock
    private RegistrationRequestService registrationRequestService;
    @Mock
    private LocationService locationService;
    @Mock
    private TmSessionService tmSessionService;
    @Mock
    private TmScheduleCalendarService tmScheduleCalendarService;
    @Mock
    private TmScheduleEditGateService scheduleEditGateService;
    @Mock
    private AuditService auditService;
    @Mock
    private UserAccountService userAccountService;

    @Test
    void addLocationStartsFromAvailableLocationsList() {
        TmMenuService service = new TmMenuService(
                registrationRequestService,
                locationService,
                tmSessionService,
                tmScheduleCalendarService,
                scheduleEditGateService,
                auditService,
                userAccountService
        );

        Long telegramUserId = 111L;
        Long chatId = 222L;

        TmSession session = TmSession.builder()
                .telegramUserId(telegramUserId)
                .state(TmState.LOCATIONS_MENU)
                .build();

        UserAccount tm = new UserAccount();
        tm.setId(UUID.randomUUID());
        tm.setRole(Role.TM);
        tm.setStatus(RegistrationStatus.APPROVED);

        Location location = new Location();
        location.setId(UUID.randomUUID());
        location.setName("ТРЦ Dream");
        location.setCode("ABCD1234");

        when(tmSessionService.findByTelegramUserId(telegramUserId)).thenReturn(Optional.of(session));
        when(tmSessionService.save(any(TmSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userAccountService.findByTelegramUserId(telegramUserId)).thenReturn(Optional.of(tm));
        when(locationService.findActiveAvailableForTm(tm.getId())).thenReturn(List.of(location));

        BotResponse response = service.handleText(telegramUserId, chatId, "Додати локацію");

        SendMessage message = (SendMessage) response.actions().get(0);
        assertEquals("Оберіть локацію для додавання:", message.getText());
        assertTrue(message.getReplyMarkup() != null);
    }

    @Test
    void addLocationInputAcceptsOnlyLocationFromList() {
        TmMenuService service = new TmMenuService(
                registrationRequestService,
                locationService,
                tmSessionService,
                tmScheduleCalendarService,
                scheduleEditGateService,
                auditService,
                userAccountService
        );

        Long telegramUserId = 111L;
        Long chatId = 222L;

        TmSession session = TmSession.builder()
                .telegramUserId(telegramUserId)
                .state(TmState.LOCATION_ADD_INPUT)
                .build();

        UserAccount tm = new UserAccount();
        tm.setId(UUID.randomUUID());
        tm.setRole(Role.TM);
        tm.setStatus(RegistrationStatus.APPROVED);

        Location location = new Location();
        location.setId(UUID.randomUUID());
        location.setName("ТРЦ Dream");
        location.setCode("ABCD1234");

        when(tmSessionService.findByTelegramUserId(telegramUserId)).thenReturn(Optional.of(session));
        when(tmSessionService.save(any(TmSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userAccountService.findByTelegramUserId(telegramUserId)).thenReturn(Optional.of(tm));
        when(locationService.findActiveAvailableForTm(tm.getId())).thenReturn(List.of(location));

        BotResponse response = service.handleText(telegramUserId, chatId, "📍 ТРЦ Dream | ABCD1234");

        SendMessage message = (SendMessage) response.actions().get(0);
        assertTrue(message.getText().contains("Додавання локації під контроль"));
        assertTrue(message.getText().contains("Назва: ТРЦ Dream"));
    }
}
