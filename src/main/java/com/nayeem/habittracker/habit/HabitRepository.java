package com.nayeem.habittracker.habit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

import java.util.Optional;

/**
 * Every query is scoped by the owner (security-checklist): a habit that isn't yours is simply
 * not found. Package-private — other features go through the habit service.
 */
interface HabitRepository extends JpaRepository<Habit, Long> {

    Optional<Habit> findByIdAndUserId(Long id, Long userId);

    List<Habit> findAllByGoalIdAndUserIdOrderById(Long goalId, Long userId);

    List<Habit> findAllByUserId(Long userId);

    /** One statement, so no loaded log can still point at the habit; the database cascades (V10). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Habit h where h.id = :id")
    void deleteHabit(@Param("id") Long id);

    Page<Habit> findAllByUserIdAndArchived(Long userId, boolean archived, Pageable pageable);

    /**
     * Active habits whose reminder time is this minute <em>in their owner's timezone</em> and that
     * aren't done (or rested) yet on the owner's today (plan §12.2). The zone conversion is
     * Postgres's, so one query serves every user. Whether the habit is due today is the caller's rule.
     */
    @Query(nativeQuery = true, value = """
            select h.id from habits h
            join users u on u.id = h.user_id
            where h.archived = false
              and h.reminder_time is not null
              and h.reminder_time = date_trunc('minute', cast(:now as timestamptz) at time zone u.timezone)::time
              and not exists (
                  select 1 from habit_logs l
                  where l.habit_id = h.id
                    and l.log_date = (cast(:now as timestamptz) at time zone u.timezone)::date
                    and (l.completed_count >= l.target_count or l.rest))
            """)
    List<Long> findRemindableAt(@Param("now") Instant now);
}
