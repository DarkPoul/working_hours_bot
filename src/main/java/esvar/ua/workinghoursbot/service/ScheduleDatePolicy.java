package esvar.ua.workinghoursbot.service;

import java.time.YearMonth;

public final class ScheduleDatePolicy {

    private ScheduleDatePolicy() {
    }

    public static boolean isEditableMonth(YearMonth month, YearMonth now) {
        if (month == null || now == null) {
            return false;
        }
        YearMonth next = now.plusMonths(1);
        return month.equals(now) || month.equals(next);
    }

    public static boolean isViewableMonth(YearMonth month, YearMonth now) {
        return isEditableMonth(month, now);
    }
}
