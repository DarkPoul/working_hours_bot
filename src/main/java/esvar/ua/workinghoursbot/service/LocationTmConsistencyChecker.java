package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.domain.Location;
import esvar.ua.workinghoursbot.domain.Role;
import esvar.ua.workinghoursbot.domain.UserAccount;
import esvar.ua.workinghoursbot.repository.LocationRepository;
import esvar.ua.workinghoursbot.repository.UserAccountRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LocationTmConsistencyChecker implements ApplicationRunner {

    private final LocationRepository locationRepository;
    private final UserAccountRepository userAccountRepository;

    @Override
    public void run(ApplicationArguments args) {
        List<Location> locations = locationRepository.findAll();
        for (Location location : locations) {
            Long tmUserId = location.getTmUserId();
            if (tmUserId == null) {
                continue;
            }
            Optional<UserAccount> tmOptional = userAccountRepository.findByTelegramUserId(tmUserId);
            if (tmOptional.isEmpty()) {
                log.warn("Location TM link mismatch. locationId={}, tmTelegramUserId={} (no user account found)",
                        location.getId(),
                        tmUserId);
                continue;
            }
            UserAccount tmAccount = tmOptional.get();
            if (tmAccount.getRole() != Role.TM) {
                log.warn("Location TM link mismatch. locationId={}, tmTelegramUserId={}, actualRole={}",
                        location.getId(),
                        tmUserId,
                        tmAccount.getRole());
            }
        }
    }
}
