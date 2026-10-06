package com.nayeem.habittracker.dashboard;

import com.nayeem.habittracker.checkin.WindowStats;
import com.nayeem.habittracker.dashboard.DashboardResponse.Period;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompletionCalculatorTest {

    @Test
    void aPeriodComparesWithThePreviousOne() {
        Period period = CompletionCalculator.period(new WindowStats(7, 6, 7, 0.86), new WindowStats(7, 4, 7, 0.57));

        assertThat(period).isEqualTo(new Period(7, 6, 7, 0.86, 0.57, 0.29));
    }

    @Test
    void noChangeWithoutSomethingToCompare() {
        assertThat(CompletionCalculator.period(new WindowStats(7, 2, 3, 0.67), new WindowStats(7, 0, 0, null)).change())
                .isNull();
        assertThat(CompletionCalculator.period(new WindowStats(7, 0, 0, null), new WindowStats(7, 0, 0, null)).change())
                .isNull();
    }

    @Test
    void overallAddsHabitsUp() {
        Period overall = CompletionCalculator.overall(7,
                List.of(new WindowStats(7, 7, 7, 1.0), new WindowStats(7, 1, 3, 0.33)),
                List.of(new WindowStats(7, 3, 7, 0.43), new WindowStats(7, 0, 0, null)));

        // (7 + 1) / (7 + 3) now; 3 / 7 before
        assertThat(overall).isEqualTo(new Period(7, 8, 10, 0.8, 0.43, 0.37));
    }

    @Test
    void extraCheckInsOfOneHabitDontCoverForAnother() {
        // 3-a-week habit done all 7 days (7 of 3 asked) next to a daily habit done 0 of 7
        Period overall = CompletionCalculator.overall(7,
                List.of(new WindowStats(7, 7, 3, 1.0), new WindowStats(7, 0, 7, 0.0)),
                List.of(new WindowStats(7, 0, 0, null)));

        assertThat(overall.done()).isEqualTo(7);
        assertThat(overall.rate()).isEqualTo(0.3);   // 3 of 10, not 7 of 10
        assertThat(overall.previousRate()).isNull();
    }

    @Test
    void noHabitsMeansNoRates() {
        assertThat(CompletionCalculator.overall(30, List.of(), List.of())).isEqualTo(new Period(30, 0, 0, null, null, null));
    }
}
