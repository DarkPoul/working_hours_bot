package esvar.ua.workinghoursbot.service;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleSessionTest {

    @Test
    void keepsDraftsPerMonth() {
        ScheduleSession session = new ScheduleSession(1L);
        YearMonth current = YearMonth.of(2024, 2);
        YearMonth next = current.plusMonths(1);
        LocalDate day = current.atDay(10);

        session.getOrCreateDraft(current).add(day);
        assertThat(session.hasDraft(current)).isTrue();
        assertThat(session.getOrCreateDraft(current)).contains(day);

        assertThat(session.hasDraft(next)).isFalse();
        assertThat(session.getOrCreateDraft(next)).isEmpty();
    }
}
