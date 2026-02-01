package esvar.ua.workinghoursbot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "registration_sessions")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrationSession {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", nullable = false, length = 36)
    private UUID id;

    @Column(name = "telegram_user_id", nullable = false, unique = true)
    private Long telegramUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    private RegistrationState state;

    @Column(name = "draft_last_name", length = 64)
    private String draftLastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "draft_role", length = 32)
    private Role draftRole;

    @Column(name = "draft_location_page")
    private Integer draftLocationPage;

    @Column(name = "tm_pin_attempts", nullable = false)
    private Integer tmPinAttempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (tmPinAttempts == null) tmPinAttempts = 0;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
