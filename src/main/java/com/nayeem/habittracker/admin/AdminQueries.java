package com.nayeem.habittracker.admin;

import com.nayeem.habittracker.admin.AdminStatsResponse.Active;
import com.nayeem.habittracker.admin.AdminStatsResponse.DayCount;
import com.nayeem.habittracker.admin.AdminStatsResponse.Usage;
import com.nayeem.habittracker.admin.AdminStatsResponse.Users;
import com.nayeem.habittracker.user.Plan;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Read-only numbers across features for admins (PLAN.md §3.2). Plain SQL over the tables rather than
 * a count method on every feature's service; it never writes — changes go through the services.
 * People numbers count users only, not admins.
 */
@Repository
@RequiredArgsConstructor
class AdminQueries {

    /** The last 30 days in UTC, today included; a day with nothing still appears, as 0. */
    private static final String LAST_30_DAYS = "generate_series((now() at time zone 'UTC')::date - 29, "
            + "(now() at time zone 'UTC')::date, interval '1 day') d";

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

    Users users() {
        List<DayCount> signUps = jdbc.sql("select d::date, count(u.id) from " + LAST_30_DAYS
                        + " left join users u on (u.created_at at time zone 'UTC')::date = d::date and u.role = 'USER'"
                        + " group by d order by d")
                .query((rs, row) -> new DayCount(rs.getObject(1, LocalDate.class), rs.getLong(2)))
                .list();
        return jdbc.sql("""
                        select count(*), count(email_verified_at), count(provider_id),
                               count(*) filter (where plan = 'PRO'), count(suspended_at)
                          from users where role = 'USER'""")
                .query((rs, row) -> new Users(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4),
                        rs.getLong(5), signUps))
                .single();
    }

    Active active() {
        return jdbc.sql("""
                        select count(distinct h.user_id) filter (where t.at >= now() - interval '1 day'),
                               count(distinct h.user_id) filter (where t.at >= now() - interval '7 days'),
                               count(distinct h.user_id)
                          from habit_logs l
                          join habits h on h.id = l.habit_id
                          join users u on u.id = h.user_id and u.role = 'USER'
                         cross join lateral (select coalesce(l.last_modified_at, l.created_at) as at) t
                         where t.at >= now() - interval '30 days'""")
                .query((rs, row) -> new Active(rs.getLong(1), rs.getLong(2), rs.getLong(3)))
                .single();
    }

    Usage usage() {
        List<DayCount> checkIns = jdbc.sql("select d::date, count(l.id) from " + LAST_30_DAYS
                        + " left join habit_logs l on l.log_date = d::date and l.completed_count > 0"
                        + " group by d order by d")
                .query((rs, row) -> new DayCount(rs.getObject(1, LocalDate.class), rs.getLong(2)))
                .list();
        return jdbc.sql("""
                        select (select count(*) from habits where not archived),
                               (select count(*) from goals where status = 'ACTIVE'),
                               (select count(distinct user_id) from push_subscriptions),
                               (select count(*) from users u where u.role = 'USER' and u.plan = 'FREE' and (
                                   (select count(*) from habits h where h.user_id = u.id and not h.archived) >= :maxHabits
                                   or (select count(*) from goals g where g.user_id = u.id and g.status = 'ACTIVE')
                                      >= :maxGoals))""")
                .param("maxHabits", Plan.FREE.getMaxActiveHabits())
                .param("maxGoals", Plan.FREE.getMaxActiveGoals())
                .query((rs, row) -> new Usage(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), checkIns))
                .single();
    }
}
