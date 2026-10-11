package com.nayeem.habittracker.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

/**
 * Read-only numbers across features for admins (PLAN.md §3.2). Plain SQL over the tables rather than
 * a count method on every feature's service; it never writes — changes go through the services.
 */
@Repository
@RequiredArgsConstructor
class AdminQueries {

    private final JdbcClient jdbc;

    AdminUserDetail detail(AdminUserResponse user) {
        return jdbc.sql("""
                        select (select count(*) from habits where user_id = :id and not archived),
                               (select count(*) from habits where user_id = :id and archived),
                               (select count(*) from habit_logs l join habits h on h.id = l.habit_id
                                 where h.user_id = :id and l.completed_count > 0),
                               (select count(*) from goals where user_id = :id),
                               (select max(coalesce(l.last_modified_at, l.created_at)) from habit_logs l
                                 join habits h on h.id = l.habit_id where h.user_id = :id),
                               (select count(*) from push_subscriptions where user_id = :id)""")
                .param("id", user.id())
                .query((rs, row) -> {
                    OffsetDateTime lastActive = rs.getObject(5, OffsetDateTime.class);
                    return new AdminUserDetail(user, rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4),
                            lastActive == null ? null : lastActive.toInstant(), rs.getLong(6));
                })
                .single();
    }
}
