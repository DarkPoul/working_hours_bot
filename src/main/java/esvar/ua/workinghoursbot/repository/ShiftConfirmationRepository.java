package esvar.ua.workinghoursbot.repository;

import esvar.ua.workinghoursbot.domain.ShiftConfirmation;
import esvar.ua.workinghoursbot.domain.ShiftConfirmationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftConfirmationRepository extends JpaRepository<ShiftConfirmation, UUID> {

    Optional<ShiftConfirmation> findByTelegramUserIdAndLocationIdAndDate(
            Long telegramUserId,
            UUID locationId,
            LocalDate date
    );

    Optional<ShiftConfirmation> findFirstByTelegramUserIdAndStatusOrderByAskedAtDesc(
            Long telegramUserId,
            ShiftConfirmationStatus status
    );

    List<ShiftConfirmation> findByStatus(ShiftConfirmationStatus status);
}
