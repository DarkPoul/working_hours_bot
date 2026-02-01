package esvar.ua.workinghoursbot.repository;

import esvar.ua.workinghoursbot.domain.ScheduleDay;
import esvar.ua.workinghoursbot.domain.ScheduleStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleDayRepository extends JpaRepository<ScheduleDay, UUID> {

    List<ScheduleDay> findByTelegramUserIdAndLocationIdAndDateBetween(
            Long telegramUserId,
            UUID locationId,
            LocalDate start,
            LocalDate end
    );

    long deleteByTelegramUserIdAndLocationIdAndDateBetween(
            Long telegramUserId,
            UUID locationId,
            LocalDate start,
            LocalDate end
    );

    boolean existsByTelegramUserIdAndDateAndStatus(Long telegramUserId, LocalDate date, ScheduleStatus status);

    boolean existsByTelegramUserIdAndLocationIdAndDateAndStatus(
            Long telegramUserId,
            UUID locationId,
            LocalDate date,
            ScheduleStatus status
    );

    long deleteByTelegramUserIdAndLocationIdAndDate(Long telegramUserId, UUID locationId, LocalDate date);

    List<ScheduleDay> findByDateAndStatus(LocalDate date, ScheduleStatus status);

    List<ScheduleDay> findByLocationIdAndDateBetween(UUID locationId, LocalDate start, LocalDate end);
}
