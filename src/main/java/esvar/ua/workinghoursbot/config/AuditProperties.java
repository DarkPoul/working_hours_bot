package esvar.ua.workinghoursbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "audit")
public record AuditProperties(
        boolean enabled,
        Long chatId
) {
}
