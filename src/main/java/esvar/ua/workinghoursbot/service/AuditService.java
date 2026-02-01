package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.config.AuditProperties;
import esvar.ua.workinghoursbot.domain.AuditEvent;
import esvar.ua.workinghoursbot.domain.AuditEventType;
import esvar.ua.workinghoursbot.repository.AuditEventRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final AuditProperties auditProperties;
    private final TelegramSender telegramSender;

    @Transactional
    public AuditEvent log(AuditEventType eventType,
                          UUID actorUserId,
                          UUID targetUserId,
                          UUID locationId,
                          String payload) {
        AuditEvent event = new AuditEvent();
        event.setEventType(eventType);
        event.setActorUserId(actorUserId);
        event.setTargetUserId(targetUserId);
        event.setLocationId(locationId);
        event.setPayload(payload);
        event.setCreatedAt(Instant.now());
        AuditEvent saved = auditEventRepository.save(event);
        sendToAuditChat(saved);
        return saved;
    }

    private void sendToAuditChat(AuditEvent event) {
        if (!auditProperties.enabled()) {
            return;
        }
        Long chatId = auditProperties.chatId();
        if (chatId == null || chatId <= 0) {
            return;
        }
        String messageText = formatMessage(event);
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(messageText)
                .build();
        telegramSender.send(message);
    }

    private String formatMessage(AuditEvent event) {
        String payload = event.getPayload() == null ? "" : event.getPayload();
        return "[" + event.getEventType() + "] " + payload;
    }
}
