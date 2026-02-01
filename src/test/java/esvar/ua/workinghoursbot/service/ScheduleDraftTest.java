package esvar.ua.workinghoursbot.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleDraftTest {

    @Test
    void togglesWorkDay() {
        ScheduleDraft draft = new ScheduleDraft(1L, UUID.randomUUID(), YearMonth.of(2024, 2), ScheduleMode.EDIT, Set.of());
        LocalDate date = LocalDate.of(2024, 2, 10);

        boolean added = draft.toggleDay(date);
        assertThat(added).isTrue();
        assertThat(draft.getWorkDays()).contains(date);

        boolean removed = draft.toggleDay(date);
        assertThat(removed).isFalse();
        assertThat(draft.getWorkDays()).doesNotContain(date);

        draft.toggleDay(date);
        draft.clear();
        assertThat(draft.getWorkDays()).isEmpty();
    }
}
