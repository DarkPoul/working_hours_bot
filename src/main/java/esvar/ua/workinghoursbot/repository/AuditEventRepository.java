package esvar.ua.workinghoursbot.repository;

import esvar.ua.workinghoursbot.domain.AuditEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
}
