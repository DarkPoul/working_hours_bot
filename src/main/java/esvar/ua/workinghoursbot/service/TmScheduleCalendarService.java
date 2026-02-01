package esvar.ua.workinghoursbot.service;

import esvar.ua.workinghoursbot.domain.Location;
import esvar.ua.workinghoursbot.domain.RegistrationStatus;
import esvar.ua.workinghoursbot.domain.Role;
import esvar.ua.workinghoursbot.domain.ScheduleDay;
import esvar.ua.workinghoursbot.domain.ScheduleStatus;
import esvar.ua.workinghoursbot.domain.SubstitutionRequest;
import esvar.ua.workinghoursbot.domain.SubstitutionRequestStatus;
import esvar.ua.workinghoursbot.domain.UserAccount;
import esvar.ua.workinghoursbot.repository.ScheduleDayRepository;
import esvar.ua.workinghoursbot.repository.SubstitutionRequestRepository;
import esvar.ua.workinghoursbot.repository.UserAccountRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

@Service
@RequiredArgsConstructor
public class TmScheduleCalendarService {

    private static final List<String> WEEKDAY_LABELS = List.of("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд");
    private static final int CELL_WIDTH = 4;
    private static final String BLUE = "🔵";
    private static final String GREEN = "🟢";
    private static final String ORANGE = "🟠";
    private static final String EMPTY_MARK = "·";

    private final ScheduleDayRepository scheduleDayRepository;
    private final SubstitutionRequestRepository substitutionRequestRepository;
    private final UserAccountRepository userAccountRepository;
    private final ScheduleCalendarKeyboardBuilder keyboardBuilder;

    public TmScheduleView buildLocationView(Location location, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        List<ScheduleDay> days = scheduleDayRepository.findByLocationIdAndDateBetween(location.getId(), start, end)
                .stream()
                .filter(day -> day.getStatus() == ScheduleStatus.WORK)
                .toList();

        Map<Long, Set<LocalDate>> workDaysByUser = days.stream()
                .collect(Collectors.groupingBy(
                        ScheduleDay::getTelegramUserId,
                        Collectors.mapping(ScheduleDay::getDate, Collectors.toSet())
                ));

        List<UserAccount> sellers = userAccountRepository.findByStatusAndRoleAndLocation_Id(
                        RegistrationStatus.APPROVED,
                        Role.SELLER,
                        location.getId()
                ).stream()
                .sorted(Comparator.comparing(UserAccount::getLastName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(UserAccount::getCreatedAt))
                .toList();

        UserAccount firstSeller = sellers.size() > 0 ? sellers.get(0) : null;
        UserAccount secondSeller = sellers.size() > 1 ? sellers.get(1) : null;

        Map<LocalDate, UserAccount> substitutionByDate = new HashMap<>();
        List<SubstitutionRequest> substitutions = substitutionRequestRepository
                .findByStatusAndLocation_IdAndRequestDateBetween(
                        SubstitutionRequestStatus.APPROVED,
                        location.getId(),
                        start,
                        end
                );
        for (SubstitutionRequest request : substitutions) {
            if (request.getReplacementUser() != null) {
                substitutionByDate.put(request.getRequestDate(), request.getReplacementUser());
            }
        }

        Set<LocalDate> firstDays = firstSeller == null
                ? Set.of()
                : workDaysByUser.getOrDefault(firstSeller.getTelegramUserId(), Set.of());
        Set<LocalDate> secondDays = secondSeller == null
                ? Set.of()
                : workDaysByUser.getOrDefault(secondSeller.getTelegramUserId(), Set.of());

        Map<UserAccount, Integer> substitutionCounts = new HashMap<>();
        int firstCount = 0;
        int secondCount = 0;
        Map<LocalDate, String> markerByDate = new HashMap<>();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (substitutionByDate.containsKey(date)) {
                markerByDate.put(date, ORANGE);
                UserAccount replacement = substitutionByDate.get(date);
                substitutionCounts.merge(replacement, 1, Integer::sum);
                continue;
            }
            if (firstDays.contains(date)) {
                markerByDate.put(date, BLUE);
                firstCount++;
                continue;
            }
            if (secondDays.contains(date)) {
                markerByDate.put(date, GREEN);
                secondCount++;
            }
        }

        StringBuilder text = new StringBuilder();
        text.append("📍 ").append(location.getName()).append("\n");
        text.append("📅 ").append(formatMonth(month)).append("\n\n");
        text.append("<pre>").append(buildTable(month, markerByDate)).append("</pre>\n");
        text.append("Легенда: 🔵 перший продавець | 🟢 другий продавець | 🟠 підміна\n");
        text.append("\nПідсумок змін:\n");
        int index = 1;
        if (firstSeller != null) {
            text.append(index++).append(") ").append(BLUE).append(" ")
                    .append(firstSeller.getLastName()).append(" — ").append(firstCount).append(" змін\n");
        }
        if (secondSeller != null) {
            text.append(index++).append(") ").append(GREEN).append(" ")
                    .append(secondSeller.getLastName()).append(" — ").append(secondCount).append(" змін\n");
        }
        List<UserAccount> replacements = substitutionCounts.keySet().stream()
                .sorted(Comparator.comparing(UserAccount::getLastName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        for (UserAccount replacement : replacements) {
            text.append(index++).append(") ").append(ORANGE).append(" ")
                    .append(replacement.getLastName()).append(" — ")
                    .append(substitutionCounts.getOrDefault(replacement, 0)).append(" змін\n");
        }

        InlineKeyboardMarkup keyboard = keyboardBuilder.buildTmViewKeyboard(location.getId(), month);
        return new TmScheduleView(text.toString().trim(), keyboard);
    }

    private String formatMonth(YearMonth month) {
        String monthName = month.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("uk"));
        return monthName + " " + month.getYear();
    }

    private String buildTable(YearMonth month, Map<LocalDate, String> markerByDate) {
        StringBuilder table = new StringBuilder();
        table.append(buildHeaderRow()).append("\n");
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
                    String marker = markerByDate.getOrDefault(date, EMPTY_MARK);
                    String cell = String.format("%02d%s", day, marker);
                    row.append(padCell(cell));
                    day++;
                }
            }
            table.append(row.toString().stripTrailing());
            if (day <= daysInMonth) {
                table.append("\n");
            }
        }
        return table.toString();
    }

    private String buildHeaderRow() {
        StringBuilder row = new StringBuilder();
        for (String label : WEEKDAY_LABELS) {
            row.append(padCell(label));
        }
        return row.toString().stripTrailing();
    }

    private String padCell(String value) {
        String padded = value == null ? "" : value;
        if (padded.length() >= CELL_WIDTH) {
            return padded + " ";
        }
        return String.format("%1$-" + CELL_WIDTH + "s", padded);
    }

    public record TmScheduleView(String text, InlineKeyboardMarkup keyboard) {
    }
}
