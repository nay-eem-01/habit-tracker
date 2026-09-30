package com.nayeem.habittracker.habit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Every query is scoped by the owner (security-checklist): a habit that isn't yours is simply
 * not found. Package-private — other features go through the habit service.
 */
interface HabitRepository extends JpaRepository<Habit, Long> {

    Optional<Habit> findByIdAndUserId(Long id, Long userId);

    Page<Habit> findAllByUserIdAndArchived(Long userId, boolean archived, Pageable pageable);
}
