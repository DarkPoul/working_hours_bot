package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.domain.Location;
import esvar.ua.workinghoursbot.domain.UserAccount;
import esvar.ua.workinghoursbot.repository.LocationRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TmScheduleEditGateService {

    private final LocationRepository locationRepository;

    public boolean isScheduleEditEnabled(UserAccount account) {
        if (account == null || account.getLocation() == null) {
            return false;
        }
        return account.getLocation().isScheduleEditEnabled();
    }

    public Optional<Location> findLocation(UUID locationId) {
        return locationRepository.findById(locationId);
    }

    @Transactional
    public Location updateScheduleEditFlag(Location location, boolean enabled) {
        location.setScheduleEditEnabled(enabled);
        return locationRepository.save(location);
    }
}
