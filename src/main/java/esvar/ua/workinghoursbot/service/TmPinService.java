package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.config.DomainProperties;
import esvar.ua.workinghoursbot.domain.UserAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TmPinService {
    private final DomainProperties domainProperties;

    public boolean verify(UserAccount account, String pinInput) {
        if (domainProperties.tm().pin() != null && domainProperties.tm().pin().equals(pinInput)) {
            account.setTmPinAttempts(0);
            return true;
        }
        account.setTmPinAttempts(account.getTmPinAttempts() + 1);
        if (account.getTmPinAttempts() >= domainProperties.tm().maxAttempts()) {
            account.setBlocked(true);
            account.setActive(false);
        }
        return false;
    }
}
