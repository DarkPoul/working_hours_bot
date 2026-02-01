package esvar.ua.workinghoursbot.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;

@Converter(autoApply = true)
public class InstantStringConverter implements AttributeConverter<Instant, String> {

    @Override
    public String convertToDatabaseColumn(Instant attribute) {
        return attribute == null ? null : attribute.toString(); // ISO-8601
    }

    @Override
    public Instant convertToEntityAttribute(String dbData) {
        return (dbData == null || dbData.isBlank()) ? null : Instant.parse(dbData);
    }
}
