package esvar.ua.workinghoursbot.service;

import java.time.YearMonth;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleDatePolicyTest {

    @Test
    void allowsCurrentAndNextMonth() {
        YearMonth now = YearMonth.of(2024, 5);
        assertThat(ScheduleDatePolicy.isEditableMonth(now, now)).isTrue();
        assertThat(ScheduleDatePolicy.isEditableMonth(now.plusMonths(1), now)).isTrue();
        assertThat(ScheduleDatePolicy.isEditableMonth(now.minusMonths(1), now)).isFalse();
        assertThat(ScheduleDatePolicy.isEditableMonth(now.plusMonths(2), now)).isFalse();
    }
}
