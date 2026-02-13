package esvar.ua.workinghoursbot.service;

import static org.junit.jupiter.api.Assertions.*;

import esvar.ua.workinghoursbot.config.DomainProperties;
import esvar.ua.workinghoursbot.domain.UserAccount;
import org.junit.jupiter.api.Test;

class TmPinServiceTest {

    @Test
    void shouldBlockAfterMaxAttempts() {
        TmPinService service = new TmPinService(new DomainProperties(
                new DomainProperties.Tm("1234", 2),
                new DomainProperties.Schedule(2)
        ));
        UserAccount account = new UserAccount();

        assertFalse(service.verify(account, "0000"));
        assertFalse(account.isBlocked());
        assertFalse(service.verify(account, "1111"));
        assertTrue(account.isBlocked());
        assertEquals(2, account.getTmPinAttempts());
    }
}
