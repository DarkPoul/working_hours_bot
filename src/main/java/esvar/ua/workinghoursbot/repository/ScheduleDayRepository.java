package esvar.ua.workinghoursbot.repository;

import esvar.ua.workinghoursbot.domain.ScheduleDay;
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

    void deleteByTelegramUserIdAndLocationIdAndDateBetween(
            Long telegramUserId,
            UUID locationId,
            LocalDate start,
            LocalDate end
    );
}
