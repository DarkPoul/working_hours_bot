package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.domain.Location;
import esvar.ua.workinghoursbot.repository.LocationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;

    public Page<Location> findActivePage(int page, int size) {
        return locationRepository.findByActiveTrueOrderBySortOrderAscNameAsc(PageRequest.of(page, size));
    }

    public Optional<Location> findActiveByCode(String code) {
        return locationRepository.findByCodeAndActiveTrue(code);
    }

    public List<Location> findActiveManagedByTmId(UUID tmId) {
        return locationRepository.findActiveByManagedTmId(tmId);
    }

    public Optional<Location> findActiveByIdAndManagedTmId(UUID id, UUID tmId) {
        return locationRepository.findActiveByIdAndManagedTmId(id, tmId);
    }

    public List<Location> findActiveAvailableForTm(UUID tmId) {
        return locationRepository.findActiveAvailableForTm(tmId);
    }

    public Optional<Location> findById(UUID id) {
        return locationRepository.findById(id);
    }

    @Transactional
    public Location createLocation(Long tmUserId, String name) {
        Location location = new Location();
        location.setId(UUID.randomUUID());
        location.setName(name);
        location.setTmUserId(tmUserId);
        location.setActive(true);
        location.setScheduleEditEnabled(false);
        location.setCode(generateUniqueCode());
        return locationRepository.save(location);
    }

    @Transactional
    public void deactivateLocation(Location location) {
        location.setActive(false);
        locationRepository.save(location);
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (locationRepository.existsByCode(code));
        return code;
    }
}
