package com.nayeem.habittracker.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Every query carries the owner; package-private like the other repositories. */
interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /** Unread first, then newest first; pass an unsorted {@link Pageable}. */
    @Query("""
            select n from Notification n where n.user.id = :userId
            order by case when n.readAt is null then 0 else 1 end, n.createdAt desc, n.id desc""")
    Page<Notification> findForUser(@Param("userId") Long userId, Pageable pageable);

    List<Notification> findAllByUserIdAndReadAtIsNull(Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);

    /**
     * Creates a reminder unless one already exists for the habit, day and type — so a restart or a
     * second app instance can't send it twice. Native SQL skips JPA auditing, so the audit columns
     * are filled here.
     *
     * @return 1 if created, 0 if it already existed
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            insert into notifications (user_id, habit_id, type, title, body, for_date,
                                       created_at, created_by, last_modified_at, last_modified_by)
            values (:userId, :habitId, :type, :title, :body, :forDate, :now, 'SYSTEM', :now, 'SYSTEM')
            on conflict (habit_id, for_date, type) do nothing
            """)
    int insertIfAbsent(@Param("userId") Long userId, @Param("habitId") Long habitId, @Param("type") String type,
                       @Param("title") String title, @Param("body") String body,
                       @Param("forDate") LocalDate forDate, @Param("now") Instant now);
}
