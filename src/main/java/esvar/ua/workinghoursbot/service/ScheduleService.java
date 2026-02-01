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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
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
        int workDaysCount = workDays == null ? 0 : workDays.size();

        log.debug("Saving schedule month. userId={}, locationId={}, month={}, start={}, end={}, workDaysCount={}",
                telegramUserId, locationId, month, start, end, workDaysCount);

        // 1. Видаляємо існуючі записи
        scheduleDayRepository.deleteByTelegramUserIdAndLocationIdAndDateBetween(
                telegramUserId,
                locationId,
                start,
                end
        );

        // 2. ВАЖЛИВО: Викликаємо flush(), щоб Hibernate виконав DELETE в SQLite перед INSERT
        scheduleDayRepository.flush();

        if (workDays == null || workDays.isEmpty()) {
            log.debug("No work days to save for userId={}, locationId={}, month={}", telegramUserId, locationId, month);
            return;
        }

        // 3. Готуємо нові записи
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

        log.debug("Prepared schedule days to save. userId={}, locationId={}, month={}, filteredCount={}",
                telegramUserId, locationId, month, toSave.size());

        // 4. Зберігаємо нові дані
        scheduleDayRepository.saveAll(toSave);
        scheduleDayRepository.flush();

        log.debug("Inserted schedule days. userId={}, locationId={}, month={}, insertedCount={}",
                telegramUserId, locationId, month, toSave.size());
    }
}