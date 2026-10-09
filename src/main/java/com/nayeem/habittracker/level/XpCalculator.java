package com.nayeem.habittracker.level;

import com.nayeem.habittracker.checkin.StreakCalculator;
import com.nayeem.habittracker.checkin.StreakUnit;
import com.nayeem.habittracker.habit.FrequencyConfig;
import com.nayeem.habittracker.habit.FrequencyType;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * XP, derived from what already exists — never stored (PLAN.md §11.3). Pure: no Spring, no database.
 *
 * <ul>
 *   <li>+10 per done day that counts — the same days the streak counts (scheduled days; at most N a
 *       week for N-times-a-week), via {@link StreakCalculator#walk}.</li>
 *   <li>+5 more per such day while the streak is at least a week long (7 days, or 1 week).</li>
 *   <li>+50 / +200 / +500 / +1500 when a run reaches 7 / 30 / 100 / 365 days — or 1 / 4 / 14 / 52
 *       weeks — once per run; a new run can earn them again.</li>
 *   <li>+500 per achieved goal.</li>
 * </ul>
 * A broken streak takes nothing back (Q8): XP already earned for past days stays.
 */
public final class XpCalculator {

    static final int PER_DONE_DAY = 10;
    static final int CONSISTENCY_BONUS = 5;
    static final int PER_ACHIEVED_GOAL = 500;

    private static final int BONUS_FROM_DAYS = 7;
    private static final int BONUS_FROM_WEEKS = 1;
    private static final Map<Integer, Integer> DAY_MILESTONES = Map.of(7, 50, 30, 200, 100, 500, 365, 1500);
    private static final Map<Integer, Integer> WEEK_MILESTONES = Map.of(1, 50, 4, 200, 14, 500, 52, 1500);

    private XpCalculator() {
    }

    /**
     * @param doneDays days where {@code completedCount >= targetCount}
     * @param start    the habit's first day, in the user's timezone
     * @param today    the user's today
     */
    public static long habitXp(FrequencyType type, FrequencyConfig config, Set<LocalDate> doneDays,
                               LocalDate start, LocalDate today) {
        return habitXp(type, config, doneDays, Set.of(), start, today);
    }

    /** @param restDays days the user rested: they earn nothing and break nothing (PLAN.md §3.4) */
    public static long habitXp(FrequencyType type, FrequencyConfig config, Set<LocalDate> doneDays,
                               Set<LocalDate> restDays, LocalDate start, LocalDate today) {
        boolean weeks = StreakCalculator.unitOf(type) == StreakUnit.WEEKS;
        int bonusFrom = weeks ? BONUS_FROM_WEEKS : BONUS_FROM_DAYS;
        Map<Integer, Integer> milestones = weeks ? WEEK_MILESTONES : DAY_MILESTONES;

        long[] xp = {0};
        int[] previousRun = {0};
        StreakCalculator.walk(type, config, doneDays, restDays, start, today, (countedDays, run) -> {
            xp[0] += (long) countedDays * PER_DONE_DAY;
            if (run >= bonusFrom) {
                xp[0] += (long) countedDays * CONSISTENCY_BONUS;
            }
            if (run > previousRun[0]) {   // the run grew to this length just now
                xp[0] += milestones.getOrDefault(run, 0);
            }
            previousRun[0] = run;
        });
        return xp[0];
    }

    public static long goalXp(long achievedGoals) {
        return achievedGoals * PER_ACHIEVED_GOAL;
    }
}
