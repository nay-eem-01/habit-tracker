package com.nayeem.habittracker.account;

import com.nayeem.habittracker.checkin.ExportedCheckIn;
import com.nayeem.habittracker.goal.GoalResponse;
import com.nayeem.habittracker.habit.HabitResponse;
import com.nayeem.habittracker.resource.ResourceResponse;
import com.nayeem.habittracker.user.UserResponse;

import java.time.Instant;
import java.util.List;

/** Everything a user keeps here, in the same shapes the API returns. Uploaded files' bytes are not included. */
public record AccountExport(
        Instant exportedAt,
        UserResponse profile,
        List<HabitResponse> habits,
        List<ExportedCheckIn> checkIns,
        List<GoalResponse> goals,
        List<ResourceResponse> resources) {
}
