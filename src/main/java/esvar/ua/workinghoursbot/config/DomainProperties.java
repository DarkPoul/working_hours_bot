package esvar.ua.workinghoursbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "domain")
public record DomainProperties(
        Tm tm,
        Schedule schedule
) {
    public record Tm(String pin, int maxAttempts) {}
    public record Schedule(int maxSellersPerLocation) {}
}
