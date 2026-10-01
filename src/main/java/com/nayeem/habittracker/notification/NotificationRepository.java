package com.nayeem.habittracker.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
