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

    @Operation(summary = "My home screen: today's habits and completion rates",
            description = "Active habits only, in my timezone. `today` lists every habit with today's count, whether "
                    + "it's due and its current streak. `completion` gives 7/30/90-day rates, overall and per habit, "
                    + "each with the previous period of the same length and the change. Same numbers as each "
                    + "habit's own streak and stats.")
    @ApiResponse(responseCode = "200", description = "The dashboard")
    @GetMapping("/api/dashboard")
    ResponseEntity<HttpResponse> dashboard(@AuthenticationPrincipal AuthUser user) {
        return HttpResponse.ok("Dashboard loaded", dashboardService.dashboard(user.id()));
    }
}
