package esvar.ua.workinghoursbot.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleRendererTest {

    @Test
    void rendersMonospacedCalendarWithHeader() {
        ScheduleRenderer renderer = new ScheduleRenderer();
        YearMonth month = YearMonth.of(2024, 1);
        Set<LocalDate> workDays = Set.of(LocalDate.of(2024, 1, 1));

        ScheduleService.ScheduleSummary summary = new ScheduleService.ScheduleSummary(1, 30, 31);
        String output = renderer.renderMonthTable("Тест", month, workDays, summary);

        assertThat(output).contains("<pre>");
        assertThat(output).contains("</pre>");
        assertThat(output).contains("Пн  Вт  Ср  Чт  Пт  Сб  Нд");
        assertThat(output).contains("01✅");
        assertThat(output).contains("02❌");
        assertThat(output).contains("Робочі: 1 | Вихідні: 30");
    }
}
