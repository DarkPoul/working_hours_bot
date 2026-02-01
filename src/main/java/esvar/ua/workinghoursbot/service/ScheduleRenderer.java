package esvar.ua.workinghoursbot.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ScheduleRenderer {

    private static final List<String> WEEKDAY_LABELS = List.of("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд");
    private static final int CELL_WIDTH = 4;

    public String renderMonthTable(String locationName,
                                   YearMonth month,
                                   Set<LocalDate> workDays,
                                   ScheduleService.ScheduleSummary summary) {
        StringBuilder header = new StringBuilder();
        header.append("📍 ").append(locationName).append("\n");
        header.append("📅 ").append(formatMonth(month)).append("\n");
        if (summary != null) {
            header.append("Робочі: ").append(summary.workingCount())
                    .append(" | Вихідні: ").append(summary.offCount())
                    .append("\n");
        }
        header.append("\n");
        StringBuilder table = new StringBuilder();
        table.append(buildHeaderRow()).append("\n");
        appendWeeks(table, month, workDays);
        String legend = "\n✅ робочий   ❌ вихідний";
        return header + "<pre>" + table + "</pre>" + legend;
    }

    private String formatMonth(YearMonth month) {
        String monthName = month.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("uk"));
        return monthName + " " + month.getYear();
    }

    private String buildHeaderRow() {
        StringBuilder row = new StringBuilder();
        for (String label : WEEKDAY_LABELS) {
            row.append(padCell(label));
        }
        return row.toString().stripTrailing();
    }

    private void appendWeeks(StringBuilder table, YearMonth month, Set<LocalDate> workDays) {
        LocalDate firstDay = month.atDay(1);
        int startIndex = firstDay.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();
        int day = 1;
        int daysInMonth = month.lengthOfMonth();
        for (int week = 0; week < 6 && day <= daysInMonth; week++) {
            StringBuilder row = new StringBuilder();
            for (int weekday = 0; weekday < 7; weekday++) {
                if (week == 0 && weekday < startIndex || day > daysInMonth) {
                    row.append(padCell(""));
                } else {
                    LocalDate date = month.atDay(day);
                    boolean work = workDays != null && workDays.contains(date);
                    String cell = String.format("%02d%s", day, work ? "✅" : "❌");
                    row.append(padCell(cell));
                    day++;
                }
            }
            table.append(row.toString().stripTrailing());
            if (day <= daysInMonth) {
                table.append("\n");
            }
        }
    }

    private String padCell(String value) {
        String padded = value == null ? "" : value;
        if (padded.length() >= CELL_WIDTH) {
            return padded + " ";
        }
        return String.format("%1$-" + CELL_WIDTH + "s", padded);
    }
}
