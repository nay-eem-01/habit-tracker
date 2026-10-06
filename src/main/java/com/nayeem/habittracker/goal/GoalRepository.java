package com.nayeem.habittracker.goal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Every query is scoped by the owner (security-checklist): a goal that isn't yours is simply not
 * found. Package-private — other features go through the goal service.
 */
interface GoalRepository extends JpaRepository<Goal, Long> {

    Optional<Goal> findByIdAndUserId(Long id, Long userId);

    Page<Goal> findAllByUserId(Long userId, Pageable pageable);

    Page<Goal> findAllByUserIdAndStatus(Long userId, GoalStatus status, Pageable pageable);

    long countByUserIdAndStatus(Long userId, GoalStatus status);

    List<Goal> findAllByUserIdAndStatusOrderById(Long userId, GoalStatus status);
}
