package com.nayeem.habittracker.checkin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

interface HabitLogRepository extends JpaRepository<HabitLog, Long> {

    /** Days the habit was done (count reached its target), for streaks and stats. */
    @Query("select l.logDate from HabitLog l where l.habit.id = :habitId and l.completedCount >= l.targetCount")
    List<LocalDate> findDoneDays(@Param("habitId") Long habitId);

    /** Done days of every habit of the user, in one query (levels add XP across all habits). */
    @Query("select new com.nayeem.habittracker.checkin.HabitDoneDay(l.habit.id, l.logDate) from HabitLog l"
            + " where l.habit.user.id = :userId and l.completedCount >= l.targetCount")
    List<HabitDoneDay> findDoneDaysOfUser(@Param("userId") Long userId);

    /** When each done day since {@code from} was first logged — for the time-of-day pattern. */
    @Query("select new com.nayeem.habittracker.checkin.CheckInTime(l.habit.id, l.logDate, l.createdAt) from HabitLog l"
            + " where l.habit.user.id = :userId and l.logDate >= :from and l.completedCount >= l.targetCount")
    List<CheckInTime> findDoneCheckInTimes(@Param("userId") Long userId, @Param("from") LocalDate from);

    /** Each of the user's habits checked in on {@code day}, with that day's count. */
    @Query("select new com.nayeem.habittracker.checkin.HabitDayCount(l.habit.id, l.completedCount) from HabitLog l"
            + " where l.habit.user.id = :userId and l.logDate = :day")
    List<HabitDayCount> findCountsOn(@Param("userId") Long userId, @Param("day") LocalDate day);

    @Query("select count(l) from HabitLog l where l.habit.id = :habitId and l.logDate between :from and :to"
            + " and l.completedCount >= l.targetCount")
    long countDoneDays(@Param("habitId") Long habitId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    List<HabitLog> findAllByHabitUserIdOrderByHabitIdAscLogDateAsc(Long userId);

    @Query("select l.logDate from HabitLog l where l.habit.id = :habitId and l.rest = true")
    List<LocalDate> findRestDays(@Param("habitId") Long habitId);

    @Query("select new com.nayeem.habittracker.checkin.HabitDoneDay(l.habit.id, l.logDate) from HabitLog l"
            + " where l.habit.user.id = :userId and l.rest = true")
    List<HabitDoneDay> findRestDaysOfUser(@Param("userId") Long userId);

    @Query("select count(l) from HabitLog l where l.habit.id = :habitId and l.rest = true"
            + " and l.logDate between :from and :to")
    long countRestDays(@Param("habitId") Long habitId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(l.restCostXp), 0) from HabitLog l where l.habit.user.id = :userId")
    long sumRestCostOfUser(@Param("userId") Long userId);

    Optional<HabitLog> findByHabitIdAndLogDate(Long habitId, LocalDate logDate);

    /** Marks the day rested, keeping any partial count; audit columns passed in as for {@link #upsert}. */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into habit_logs (habit_id, log_date, completed_count, target_count, rest, rest_cost_xp,
                                    created_at, created_by, last_modified_at, last_modified_by)
            values (:habitId, :logDate, 0, :targetCount, true, :cost, :now, :actor, :now, :actor)
            on conflict (habit_id, log_date) do update set
                rest             = true,
                rest_cost_xp     = excluded.rest_cost_xp,
                last_modified_at = excluded.last_modified_at,
                last_modified_by = excluded.last_modified_by
            """)
    void markRest(@Param("habitId") Long habitId, @Param("logDate") LocalDate logDate,
                  @Param("targetCount") int targetCount, @Param("cost") int cost, @Param("now") Instant now,
                  @Param("actor") String actor);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update HabitLog l set l.rest = false, l.restCostXp = 0"
            + " where l.habit.id = :habitId and l.logDate = :day and l.rest = true")
    int clearRest(@Param("habitId") Long habitId, @Param("day") LocalDate day);

    Page<HabitLog> findAllByHabitIdAndLogDateBetween(Long habitId, LocalDate from, LocalDate to, Pageable pageable);

    /**
     * Insert-or-update in one statement, so two check-ins racing for the same day can't both
     * insert. The habit's current target is stored with the count, so the day stays judged by the
     * target it was logged against. A null note keeps the stored one; an empty note clears it. Native SQL bypasses
     * JPA auditing, so the audit columns are passed in.
     *
     * @return the row's id
     */
    @Query(nativeQuery = true, value = """
            insert into habit_logs (habit_id, log_date, completed_count, target_count, note,
                                    created_at, created_by, last_modified_at, last_modified_by)
            values (:habitId, :logDate, :completedCount, :targetCount, nullif(:note, ''), :now, :actor, :now, :actor)
            on conflict (habit_id, log_date) do update set
                completed_count  = excluded.completed_count,
                target_count     = excluded.target_count,
                rest             = false,
                rest_cost_xp     = 0,
                note             = case when :note is null then habit_logs.note else nullif(:note, '') end,
                last_modified_at = excluded.last_modified_at,
                last_modified_by = excluded.last_modified_by
            returning id
            """)
    Long upsert(@Param("habitId") Long habitId, @Param("logDate") LocalDate logDate,
                @Param("completedCount") int completedCount, @Param("targetCount") int targetCount,
                @Param("note") String note,
                @Param("now") Instant now, @Param("actor") String actor);
}
