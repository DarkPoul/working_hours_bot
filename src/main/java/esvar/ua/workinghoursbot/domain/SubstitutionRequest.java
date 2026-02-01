package esvar.ua.workinghoursbot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "substitution_request")
@Getter
@Setter
public class SubstitutionRequest {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", nullable = false, length = 36)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_user_id", nullable = false)
    private UserAccount requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Convert(converter = LocalDateStringConverter.class)
    @Column(name = "request_date", nullable = false, length = 10)
    private LocalDate requestDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SubstitutionRequestStatus status;

    @Column(name = "urgent", nullable = false)
    private boolean urgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", length = 16)
    private SubstitutionRequestScope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replacement_user_id")
    private UserAccount replacementUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposed_replacement_user_id")
    private UserAccount proposedReplacementUser;

    @Column(name = "tm_user_id")
    private Long tmUserId;

    @Column(name = "tm_decision", length = 16)
    private String tmDecision;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tm_decided_at")
    private Instant tmDecidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_user_id")
    private UserAccount resolvedByUser;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (version == null) {
            version = 0;
        }
    }
}
