package esvar.ua.workinghoursbot.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScheduleDraft {

    private final Long telegramUserId;
    private final UUID locationId;
    private final ScheduleMode mode;
    private YearMonth yearMonth;
    private final Set<LocalDate> workDays;
    private Long messageChatId;
    private Integer messageId;

    public ScheduleDraft(Long telegramUserId, UUID locationId, YearMonth yearMonth, ScheduleMode mode, Set<LocalDate> workDays) {
        this.telegramUserId = telegramUserId;
        this.locationId = locationId;
        this.yearMonth = yearMonth;
        this.mode = mode;
        this.workDays = workDays == null ? ConcurrentHashMap.newKeySet() : ConcurrentHashMap.newKeySet(workDays.size());
        if (workDays != null) {
            this.workDays.addAll(workDays);
        }
    }

    public Long getTelegramUserId() {
        return telegramUserId;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public ScheduleMode getMode() {
        return mode;
    }

    public YearMonth getYearMonth() {
        return yearMonth;
    }

    public void setYearMonth(YearMonth yearMonth) {
        this.yearMonth = yearMonth;
    }

    public Set<LocalDate> getWorkDays() {
        return workDays;
    }

    public Long getMessageChatId() {
        return messageChatId;
    }

    public void setMessageChatId(Long messageChatId) {
        this.messageChatId = messageChatId;
    }

    public Integer getMessageId() {
        return messageId;
    }

    public void setMessageId(Integer messageId) {
        this.messageId = messageId;
    }

    public boolean toggleDay(LocalDate date) {
        if (workDays.contains(date)) {
            workDays.remove(date);
            return false;
        }
        workDays.add(date);
        return true;
    }

    public void clear() {
        workDays.clear();
    }
}
