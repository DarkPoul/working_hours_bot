package esvar.ua.workinghoursbot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_events")
@Getter
@Setter
public class AuditEvent {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", nullable = false, length = 36)
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private AuditEventType eventType;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "actor_user_id", length = 36)
    private UUID actorUserId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "target_user_id", length = 36)
    private UUID targetUserId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "location_id", length = 36)
    private UUID locationId;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "message_context")
    private String messageContext;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
