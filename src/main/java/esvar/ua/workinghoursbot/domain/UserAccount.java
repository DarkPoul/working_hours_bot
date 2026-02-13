package esvar.ua.workinghoursbot.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "user_accounts")
@Getter
@Setter
public class UserAccount {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", nullable = false, length = 36)
    private UUID id;

    @Column(name = "telegram_user_id", nullable = false, unique = true)
    private Long telegramUserId;

    @Column(name = "telegram_chat_id", nullable = false)
    private Long telegramChatId;

    @Column(name = "last_name", nullable = false, length = 64)
    private String lastName;

    @Column(name = "full_name", length = 128)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pending_location_id")
    private Location pendingLocation;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "tm_locations",
            joinColumns = @JoinColumn(name = "tm_user_id"),
            inverseJoinColumns = @JoinColumn(name = "location_id")
    )
    private Set<Location> managedLocations = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RegistrationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "seller_status", length = 32)
    private SellerStatus sellerStatus;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "blocked", nullable = false)
    private boolean blocked;

    @Column(name = "tm_pin_attempts", nullable = false)
    private int tmPinAttempts;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 64)
    private UserState state;

    @Column(name = "state_payload", length = 1000)
    private String statePayload;

    @Column(name = "created_at", nullable = false, length = 32)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, length = 32)
    private Instant updatedAt;

    @Column(name = "approved_by_telegram_user_id")
    private Long approvedByTelegramUserId;

    @Column(name = "approved_at", length = 32)
    private Instant approvedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
