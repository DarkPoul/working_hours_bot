package esvar.ua.workinghoursbot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "schedule_days", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"telegram_user_id", "location_id", "date"})
})
@Getter
@Setter
public class ScheduleDay {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", nullable = false, length = 36)
    private UUID id;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "location_id", nullable = false, length = 36)
    private UUID locationId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ScheduleStatus status;

    @Column(name = "updated_at", nullable = false, length = 32)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
