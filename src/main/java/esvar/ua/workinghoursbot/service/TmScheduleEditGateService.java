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
@Transactional(readOnly = true)
public class TmScheduleEditGateService {

    private final LocationRepository locationRepository;

    public boolean isScheduleEditEnabled(UserAccount account) {
        if (account == null || account.getId() == null) return false;

        // якщо в account є locationId — краще так
        if (account.getLocation() != null && account.getLocation().getId() != null) {
            return locationRepository
                    .findScheduleEditEnabledById(account.getLocation().getId())
                    .orElse(false);
        }

        // або через accountId
        return locationRepository
                .findScheduleEditEnabledByAccount_Id(account.getId())
                .orElse(false);
    }

    @Transactional // перевизначає readOnly=true на рівні класу
    public Location updateScheduleEditFlag(Location location, boolean newValue) {
        if (location == null || location.getId() == null) {
            throw new IllegalArgumentException("Location and its id must not be null");
        }

        UUID locationId = location.getId();

        // Беремо керовану ентіті з persistence context
        Location managed = locationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Location with id %s not found".formatted(locationId)));

        // Якщо значення вже таке саме – можна просто повернути
        if (managed.isScheduleEditEnabled() == newValue) {
            return managed;
        }

        managed.setScheduleEditEnabled(newValue);

        // За бажанням можна явно зберегти;
        // якщо репозиторій – JpaRepository, save необов'язковий, але не завадить.
        return locationRepository.save(managed);
    }
}
