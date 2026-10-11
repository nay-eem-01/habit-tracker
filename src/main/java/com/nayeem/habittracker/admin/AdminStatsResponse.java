package com.nayeem.habittracker.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** App-wide numbers for the admin dashboard. Days are UTC; "active" means logged anything. */
public record AdminStatsResponse(Users users, Active active, Usage usage, Delivery delivery) {

    public record Users(long total, long verified,
                        @Schema(description = "Can sign in with Google") long withGoogle,
                        long pro, long suspended,
                        @Schema(description = "Sign-ups per day, last 30 days, oldest first") List<DayCount> signUps) {
    }

    @Schema(description = "Distinct users who logged a check-in, slip or rest day in the last 24 hours / 7 / 30 days")
    public record Active(long day, long week, long month) {
    }

    public record Usage(long activeHabits, long activeGoals,
                        @Schema(description = "Users with at least one browser getting push") long pushUsers,
                        @Schema(description = "Free users at their habit or goal limit: likely to want Pro")
                        long freeUsersAtLimit,
                        @Schema(description = "Done check-ins per day (by the user's date), last 30 days")
                        List<DayCount> checkIns) {
    }

    @Schema(description = "Counted in memory since the app started (`since`); a restart sets them to zero")
    public record Delivery(Instant since, long emailsSent, long emailsFailed, long pushesSent,
                           @Schema(description = "Browser had dropped the subscription; it was deleted")
                           long pushesGone, long pushesFailed) {
    }

    public record DayCount(LocalDate date, long count) {
    }
}
