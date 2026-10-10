package com.nayeem.habittracker.checkin;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import com.nayeem.habittracker.common.pagination.PageRequests;
import com.nayeem.habittracker.common.response.PageResponse;
import com.nayeem.habittracker.habit.Habit;
import com.nayeem.habittracker.habit.HabitService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

/** Check-ins (PLAN.md §12.1). "Today" is always the habit owner's today, in their timezone. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckInService {

    static final int MAX_DAYS_BACK = 7;
    static final int DEFAULT_RANGE_DAYS = 30;
    static final int MAX_RANGE_DAYS = 366;

    private final HabitService habitService;
    private final HabitLogRepository habitLogRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    @Transactional
    public HabitLogResponse checkIn(Long userId, Long habitId, CheckInRequest request) {
        Habit habit = habitService.getOwnedHabit(userId, habitId);
        if (habit.isArchived()) {
            throw new ApplicationException(ErrorCode.HABIT_ARCHIVED);
        }
        ZoneId zone = ZoneId.of(habit.getUser().getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate date = request.getDate() == null ? today : request.getDate();
        checkDateAllowed(date, today, habit.getCreatedAt().atZone(zone).toLocalDate());

        int count = request.getCompletedCount() == null ? habit.getTargetCount() : request.getCompletedCount();
        Long id = habitLogRepository.upsert(habit.getId(), date, count, habit.getTargetCount(), request.getNote(),
                clock.instant(), habit.getUser().getEmail());

        HabitLog saved = habitLogRepository.findById(id).orElseThrow();
        entityManager.refresh(saved); // the row may already be in this persistence context, stale
        log.info("Check-in habit {} on {}: {}/{}", habit.getId(), date, count, habit.getTargetCount());
        return HabitLogResponse.from(saved);
    }

    /**
     * A habit's logs between two days (inclusive), newest first. Defaults to the last 30 days up
     * to today; a range is at most {@value #MAX_RANGE_DAYS} days. Archived habits stay readable.
     */
    @Transactional(readOnly = true)
    public PageResponse<HabitLogResponse> logs(Long userId, Long habitId, LocalDate from, LocalDate to,
                                               int page, int size) {
        Habit habit = habitService.getOwnedHabit(userId, habitId);
        LocalDate end = to != null ? to : today(habit);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_RANGE_DAYS - 1);
        if (start.isAfter(end) || start.plusDays(MAX_RANGE_DAYS).isBefore(end.plusDays(1))) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED,
                    "from must be on or before to, at most " + MAX_RANGE_DAYS + " days apart");
        }
        var pageable = PageRequests.of(page, size, "logDate", "desc", Set.of("logDate"));
        return PageResponse.from(
                habitLogRepository.findAllByHabitIdAndLogDateBetween(habit.getId(), start, end, pageable),
                HabitLogResponse::from);
    }

    private LocalDate today(Habit habit) {
        return LocalDate.now(clock.withZone(ZoneId.of(habit.getUser().getTimezone())));
    }

    /** Not in the future, not before the habit existed, at most {@value #MAX_DAYS_BACK} days back. */
    static void checkDateAllowed(LocalDate date, LocalDate today, LocalDate habitStart) {
        if (date.isAfter(today)) {
            throw outOfRange("You can't check in for a future day");
        }
        if (date.isBefore(today.minusDays(MAX_DAYS_BACK))) {
            throw outOfRange("You can only check in up to " + MAX_DAYS_BACK + " days back");
        }
        if (date.isBefore(habitStart)) {
            throw outOfRange("The habit didn't exist on that day");
        }
    }

    private static ApplicationException outOfRange(String message) {
        return new ApplicationException(ErrorCode.LOG_DATE_OUT_OF_RANGE, message);
    }
}
