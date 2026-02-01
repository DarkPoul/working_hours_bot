package esvar.ua.workinghoursbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import esvar.ua.workinghoursbot.config.AuditProperties;
import esvar.ua.workinghoursbot.domain.AuditEvent;
import esvar.ua.workinghoursbot.domain.AuditEventType;
import esvar.ua.workinghoursbot.repository.AuditEventRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private TelegramSender telegramSender;

    @Test
    void logStoresEventAndSendsMessageWhenEnabled() {
        AuditProperties properties = new AuditProperties(true, 123L);
        AuditService auditService = new AuditService(auditEventRepository, properties, telegramSender);
        when(auditEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UUID actorId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        auditService.log(AuditEventType.USER_START, actorId, null, locationId, "payload");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        AuditEvent saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo(AuditEventType.USER_START);
        assertThat(saved.getActorUserId()).isEqualTo(actorId);
        assertThat(saved.getLocationId()).isEqualTo(locationId);
        verify(telegramSender).send(any(org.telegram.telegrambots.meta.api.methods.send.SendMessage.class));
    }

    @Test
    void logStoresEventWithoutSendingWhenDisabled() {
        AuditProperties properties = new AuditProperties(false, 123L);
        AuditService auditService = new AuditService(auditEventRepository, properties, telegramSender);
        when(auditEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        auditService.log(AuditEventType.USER_START, null, null, null, "payload");

        verify(auditEventRepository).save(any(AuditEvent.class));
        verify(telegramSender, never()).send(any(org.telegram.telegrambots.meta.api.methods.send.SendMessage.class));
    }
}
