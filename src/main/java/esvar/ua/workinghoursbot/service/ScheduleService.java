package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.domain.UserAccount;
import esvar.ua.workinghoursbot.repository.UserAccountRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final SchedulePersistenceService schedulePersistenceService;
    private final UserAccountRepository userAccountRepository;

    public ScheduleSummary getMonthSummary(Long telegramUserId, int year, int month) {
        UserAccount account = userAccountRepository.findByTelegramUserId(telegramUserId)
                .orElseThrow(() -> new IllegalStateException("Користувача не знайдено."));
        if (account.getLocation() == null) {
            throw new IllegalStateException("Спочатку оберіть локацію.");
        }
        UUID locationId = account.getLocation().getId();
        YearMonth target = YearMonth.of(year, month);
        Set<LocalDate> workDays = schedulePersistenceService.loadMonth(telegramUserId, locationId, target);
        int workingCount = workDays == null ? 0 : workDays.size();
        int daysInMonth = target.lengthOfMonth();
        int offCount = Math.max(0, daysInMonth - workingCount);
        return new ScheduleSummary(workingCount, offCount, daysInMonth);
    }

    public record ScheduleSummary(int workingCount, int offCount, int daysInMonth) {
    }
}
