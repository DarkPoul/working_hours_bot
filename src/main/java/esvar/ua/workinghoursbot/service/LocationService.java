package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.domain.Location;
import esvar.ua.workinghoursbot.repository.LocationRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

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
}
