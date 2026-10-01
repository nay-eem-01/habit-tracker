package com.nayeem.habittracker.checkin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

interface HabitLogRepository extends JpaRepository<HabitLog, Long> {

    /** Days the habit was done (count reached its target), for streaks and stats. */
    @Query("select l.logDate from HabitLog l where l.habit.id = :habitId and l.completedCount >= l.habit.targetCount")
    List<LocalDate> findDoneDays(@Param("habitId") Long habitId);

    @Query("select count(l) from HabitLog l where l.habit.id = :habitId and l.logDate between :from and :to"
            + " and l.completedCount >= l.habit.targetCount")
    long countDoneDays(@Param("habitId") Long habitId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    Page<HabitLog> findAllByHabitIdAndLogDateBetween(Long habitId, LocalDate from, LocalDate to, Pageable pageable);

    /**
     * Insert-or-update in one statement, so two check-ins racing for the same day can't both
     * insert. A null note keeps the stored one; an empty note clears it. Native SQL bypasses
     * JPA auditing, so the audit columns are passed in.
     *
     * @return the row's id
     */
    @Query(nativeQuery = true, value = """
            insert into habit_logs (habit_id, log_date, completed_count, note,
                                    created_at, created_by, last_modified_at, last_modified_by)
            values (:habitId, :logDate, :completedCount, nullif(:note, ''), :now, :actor, :now, :actor)
            on conflict (habit_id, log_date) do update set
                completed_count  = excluded.completed_count,
                note             = case when :note is null then habit_logs.note else nullif(:note, '') end,
                last_modified_at = excluded.last_modified_at,
                last_modified_by = excluded.last_modified_by
            returning id
            """)
    Long upsert(@Param("habitId") Long habitId, @Param("logDate") LocalDate logDate,
                @Param("completedCount") int completedCount, @Param("note") String note,
                @Param("now") Instant now, @Param("actor") String actor);
}
