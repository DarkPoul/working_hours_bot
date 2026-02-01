package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.domain.ScheduleDay;
import esvar.ua.workinghoursbot.domain.ScheduleStatus;
import esvar.ua.workinghoursbot.repository.ScheduleDayRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleDayRepository scheduleDayRepository;

    public Set<LocalDate> loadWorkDays(Long telegramUserId, UUID locationId, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        return scheduleDayRepository.findByTelegramUserIdAndLocationIdAndDateBetween(
                        telegramUserId,
                        locationId,
                        start,
                        end
                ).stream()
                .filter(day -> day.getStatus() == ScheduleStatus.WORK)
                .map(ScheduleDay::getDate)
                .collect(Collectors.toSet());
    }

    @Transactional
    public void saveMonth(Long telegramUserId, UUID locationId, YearMonth month, Set<LocalDate> workDays) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        scheduleDayRepository.deleteByTelegramUserIdAndLocationIdAndDateBetween(
                telegramUserId,
                locationId,
                start,
                end
        );
        if (workDays == null || workDays.isEmpty()) {
            return;
        }
        List<ScheduleDay> toSave = workDays.stream()
                .filter(date -> !date.isBefore(start) && !date.isAfter(end))
                .map(date -> {
                    ScheduleDay day = new ScheduleDay();
                    day.setTelegramUserId(telegramUserId);
                    day.setLocationId(locationId);
                    day.setDate(date);
                    day.setStatus(ScheduleStatus.WORK);
                    return day;
                })
                .toList();
        scheduleDayRepository.saveAll(toSave);
    }
}
