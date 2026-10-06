package com.nayeem.habittracker.dashboard;

import com.nayeem.habittracker.common.AppConstants;
import com.nayeem.habittracker.common.response.HttpResponse;
import com.nayeem.habittracker.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard")
@SecurityRequirement(name = AppConstants.JWT_TOKEN)
@RestController
@RequiredArgsConstructor
class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "My home screen: today, completion, highlights, goals and level",
            description = "Active habits only, in my timezone. `today` lists every habit with today's count, whether "
                    + "it's due and its current streak. `completion` gives 7/30/90-day rates, overall and per habit, "
                    + "each with the previous period of the same length and the change. `atRisk`: streaks of 3+ "
                    + "that end unless something happens today. `best` / `slipping`: top 3 by 30-day rate / by drop. "
                    + "`goals`: active goals with progress. `level`: as GET /api/me/level. Same numbers as the "
                    + "habit, goal and level endpoints.")
    @ApiResponse(responseCode = "200", description = "The dashboard")
    @GetMapping("/api/dashboard")
    ResponseEntity<HttpResponse> dashboard(@AuthenticationPrincipal AuthUser user) {
        return HttpResponse.ok("Dashboard loaded", dashboardService.dashboard(user.id()));
    }

    @Operation(summary = "My patterns: heatmap, weekdays, time of day",
            description = "Active habits, in my timezone. `heatmap`: the last 365 days, each with done / expected habits "
                    + "(N-a-week habits only add to the days they were done). `weekdays`: completion by weekday "
                    + "over the last 12 full weeks, daily and chosen-weekday habits only, with the weakest and "
                    + "strongest day. `hours`: when done check-ins of the last 90 days happened (first check-in, "
                    + "same-day only), with the peak hour.")
    @ApiResponse(responseCode = "200", description = "The patterns")
    @GetMapping("/api/dashboard/patterns")
    ResponseEntity<HttpResponse> patterns(@AuthenticationPrincipal AuthUser user) {
        return HttpResponse.ok("Patterns loaded", dashboardService.patterns(user.id()));
    }
}
