package esvar.ua.workinghoursbot.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScheduleSession {

    private final Long telegramUserId;
    private InteractionMode mode;
    private UUID activeLocationId;
    private YearMonth activeYearMonth;
    private final Map<YearMonth, Set<LocalDate>> draftWorkDaysByMonth;
    private Long calendarChatId;
    private Integer calendarMessageId;
    private Instant lastUpdatedAt;

    public ScheduleSession(Long telegramUserId) {
        this.telegramUserId = telegramUserId;
        this.mode = InteractionMode.NORMAL;
        this.draftWorkDaysByMonth = new ConcurrentHashMap<>();
        this.lastUpdatedAt = Instant.now();
    }

    public Long getTelegramUserId() {
        return telegramUserId;
    }

    public InteractionMode getMode() {
        return mode;
    }

    public void setMode(InteractionMode mode) {
        this.mode = mode;
        touch();
    }

    public UUID getActiveLocationId() {
        return activeLocationId;
    }

    public void setActiveLocationId(UUID activeLocationId) {
        this.activeLocationId = activeLocationId;
        touch();
    }

    public YearMonth getActiveYearMonth() {
        return activeYearMonth;
    }

    public void setActiveYearMonth(YearMonth activeYearMonth) {
        this.activeYearMonth = activeYearMonth;
        touch();
    }

    public Set<LocalDate> getOrCreateDraft(YearMonth month) {
        return draftWorkDaysByMonth.computeIfAbsent(month, key -> ConcurrentHashMap.newKeySet());
    }

    public boolean hasDraft(YearMonth month) {
        return draftWorkDaysByMonth.containsKey(month);
    }

    public void clearDrafts() {
        draftWorkDaysByMonth.clear();
        touch();
    }

    public Long getCalendarChatId() {
        return calendarChatId;
    }

    public void setCalendarChatId(Long calendarChatId) {
        this.calendarChatId = calendarChatId;
        touch();
    }

    public Integer getCalendarMessageId() {
        return calendarMessageId;
    }

    public void setCalendarMessageId(Integer calendarMessageId) {
        this.calendarMessageId = calendarMessageId;
        touch();
    }

    public Instant getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    private void touch() {
        this.lastUpdatedAt = Instant.now();
    }
}
