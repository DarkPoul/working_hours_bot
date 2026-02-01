package esvar.ua.workinghoursbot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
@Table(name = "shift_confirmations", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"telegram_user_id", "location_id", "shift_date"})
})
@Getter
@Setter
public class ShiftConfirmation {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", nullable = false, length = 36)
    private UUID id;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "location_id", nullable = false, length = 36)
    private UUID locationId;

    @Convert(converter = LocalDateStringConverter.class)
    @Column(name = "shift_date", nullable = false, length = 10)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ShiftConfirmationStatus status;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "asked_at", nullable = false)
    private Instant askedAt;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "auto_rejected", nullable = false)
    private boolean autoRejected;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (askedAt == null) {
            askedAt = Instant.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        if (askedAt == null) {
            askedAt = Instant.now();
        }
    }
}
