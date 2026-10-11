package com.nayeem.habittracker.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** One user's account and how much they use the app: counts only, no habit names, notes or links. */
public record AdminUserDetail(
        AdminUserResponse user,
        long activeHabits,
        long archivedHabits,
        @Schema(description = "Days logged with a count above zero, all habits") long checkIns,
        long goals,
        @Schema(description = "Last time they logged anything; null if never") Instant lastActiveAt,
        @Schema(description = "Browsers receiving push reminders") long pushSubscriptions) {
}
