package esvar.ua.workinghoursbot.service;

import java.time.YearMonth;
import java.util.UUID;

public record ScheduleDraftKey(Long telegramUserId, UUID locationId, YearMonth yearMonth, ScheduleMode mode) {
}
