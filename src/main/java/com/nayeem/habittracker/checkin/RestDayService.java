package com.nayeem.habittracker.checkin;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import com.nayeem.habittracker.habit.FrequencyType;
import com.nayeem.habittracker.habit.Habit;
import com.nayeem.habittracker.habit.HabitKind;
import com.nayeem.habittracker.habit.HabitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * Rest days (PLAN.md §3.4): one habit, one scheduled day off that neither breaks nor extends the
 * streak. Daily and chosen-weekday build habits only — an N-a-week habit already has its slack, a
 * quit habit has nothing to rest from. Same dates as a check-in. The week's first is free, the 2nd
 * and 3rd cost XP from the spendable balance; taking one back refunds it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestDayService {

    /** XP the 1st, 2nd and 3rd rest day of a habit's Monday–Sunday week cost; there is no 4th. */
    static final int[] COST = {0, 100, 200};

    private final HabitService habitService;
    private final HabitLogRepository habitLogRepository;
    private final XpBalance xpBalance;
    private final Clock clock;

    /** Rests the habit on {@code date} (today when null); resting a rested day again is fine. */
    @Transactional
    public HabitLogResponse rest(Long userId, Long habitId, LocalDate date) {
        Habit habit = habitService.getOwnedHabitForUpdate(userId, habitId);   // so two rests can't both be "the 2nd"
        if (habit.isArchived()) {
            throw new ApplicationException(ErrorCode.HABIT_ARCHIVED);
        }
        ZoneId zone = ZoneId.of(habit.getUser().getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate day = date == null ? today : date;
        CheckInService.checkDateAllowed(day, today, habit.getCreatedAt().atZone(zone).toLocalDate());
        checkRestable(habit, day);

        var existing = habitLogRepository.findByHabitIdAndLogDate(habit.getId(), day);
        if (existing.filter(HabitLog::isRest).isPresent()) {
            return HabitLogResponse.from(existing.get());
        }
        if (existing.filter(HabitLog::isDone).isPresent()) {
            throw new ApplicationException(ErrorCode.REST_DAY_DONE);
        }
        LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int restsThisWeek = (int) habitLogRepository.countRestDays(habit.getId(), monday, monday.plusDays(6));
        if (restsThisWeek >= COST.length) {
            throw new ApplicationException(ErrorCode.REST_LIMIT_REACHED);
        }
        int cost = COST[restsThisWeek];
        // shortcut: two paid rests on different habits at once can both pass this; lock the user if it matters
        if (cost > 0 && xpBalance.balance(userId) < cost) {
            throw new ApplicationException(ErrorCode.XP_NOT_ENOUGH, "This rest day costs " + cost + " XP");
        }
        habitLogRepository.markRest(habit.getId(), day, habit.getTargetCount(), cost, clock.instant(),
                habit.getUser().getEmail());
        log.info("Habit {} rested on {} by user {} for {} XP", habit.getId(), day, userId, cost);
        return HabitLogResponse.from(habitLogRepository.findByHabitIdAndLogDate(habit.getId(), day).orElseThrow());
    }

    /** Takes the rest day back; not rested is a no-op. */
    @Transactional
    public void cancel(Long userId, Long habitId, LocalDate date) {
        Habit habit = habitService.getOwnedHabit(userId, habitId);
        if (habitLogRepository.clearRest(habit.getId(), date) > 0) {
            log.info("Rest day of habit {} on {} cancelled by user {}", habit.getId(), date, userId);
        }
    }

    private static void checkRestable(Habit habit, LocalDate day) {
        if (habit.getKind() != HabitKind.BUILD || habit.getFrequencyType() == FrequencyType.X_TIMES_PER_WEEK) {
            throw new ApplicationException(ErrorCode.REST_NOT_ALLOWED,
                    "Only daily and chosen-weekday habits have rest days");
        }
        if (habit.getFrequencyType() == FrequencyType.SPECIFIC_DAYS
                && !habit.getFrequencyConfig().days().contains(day.getDayOfWeek())) {
            throw new ApplicationException(ErrorCode.REST_NOT_ALLOWED, "The habit isn't due that day anyway");
        }
    }
}
