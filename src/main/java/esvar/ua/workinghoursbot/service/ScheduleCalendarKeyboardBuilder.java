package esvar.ua.workinghoursbot.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

@Component
public class ScheduleCalendarKeyboardBuilder {

    private static final String CALLBACK_NOOP = "noop";
    private static final String EMPTY_CELL = "·";
    private static final boolean USE_LEADING_ZERO = true;

    public InlineKeyboardMarkup buildEditKeyboard(YearMonth month, Set<LocalDate> workDays) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(buildWeekdayHeader());
        rows.addAll(buildCalendarRows(month, workDays));
        rows.add(buildControlRow("🗑 Очистити", "E:C", "❌ Відміна", "E:X"));
        rows.add(buildMonthNavigationRow(month));
        rows.add(List.of(button("💾 Зберегти", "E:S")));
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    public InlineKeyboardMarkup buildViewKeyboard(YearMonth month) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(buildViewNavigationRow(month));
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    private List<InlineKeyboardButton> buildWeekdayHeader() {
        return List.of(
                button("Пн", CALLBACK_NOOP),
                button("Вт", CALLBACK_NOOP),
                button("Ср", CALLBACK_NOOP),
                button("Чт", CALLBACK_NOOP),
                button("Пт", CALLBACK_NOOP),
                button("Сб", CALLBACK_NOOP),
                button("Нд", CALLBACK_NOOP)
        );
    }

    private List<List<InlineKeyboardButton>> buildCalendarRows(YearMonth month, Set<LocalDate> workDays) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        LocalDate firstDay = month.atDay(1);
        int daysInMonth = month.lengthOfMonth();
        int firstWeekdayIndex = firstDay.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();
        int dayCounter = 1;
        for (int week = 0; week < 6; week++) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            for (int dayOfWeek = 0; dayOfWeek < 7; dayOfWeek++) {
                if (week == 0 && dayOfWeek < firstWeekdayIndex || dayCounter > daysInMonth) {
                    row.add(button(EMPTY_CELL, CALLBACK_NOOP));
                    continue;
                }
                LocalDate date = month.atDay(dayCounter);
                boolean isWork = workDays != null && workDays.contains(date);
                String label = formatDayLabel(dayCounter, isWork);
                row.add(button(label, "E:D:" + date));
                dayCounter++;
            }
            rows.add(row);
            if (dayCounter > daysInMonth) {
                break;
            }
        }
        return rows;
    }

    private List<InlineKeyboardButton> buildControlRow(String leftLabel, String leftAction, String rightLabel, String rightAction) {
        return List.of(button(leftLabel, leftAction), button(rightLabel, rightAction));
    }

    private List<InlineKeyboardButton> buildMonthNavigationRow(YearMonth month) {
        return List.of(
                button("◀️", "E:P"),
                button(formatMonthLabel(month), CALLBACK_NOOP),
                button("▶️", "E:N")
        );
    }

    private List<InlineKeyboardButton> buildViewNavigationRow(YearMonth month) {
        return List.of(
                button("◀️", "V:P"),
                button(formatMonthLabel(month), CALLBACK_NOOP),
                button("▶️", "V:N")
        );
    }

    private String formatMonthLabel(YearMonth month) {
        return month.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("uk"))
                + " " + month.getYear();
    }

    private String formatDayLabel(int day, boolean isWork) {
        String dayText = USE_LEADING_ZERO && day < 10 ? "0" + day : Integer.toString(day);
        return dayText + (isWork ? "✅" : "❌");
    }

    private InlineKeyboardButton button(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }
}
