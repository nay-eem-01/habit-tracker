package com.nayeem.habittracker.admin;

import com.nayeem.habittracker.common.AppConstants;
import com.nayeem.habittracker.common.response.HttpResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admins only (SecurityConfig); anyone else gets 404. */
@Tag(name = "Admin")
@SecurityRequirement(name = AppConstants.JWT_TOKEN)
@RestController
@RequiredArgsConstructor
class AdminStatsController {

    private final AdminService adminService;

    @Operation(summary = "App-wide numbers: users and sign-ups, active users, usage, email and push delivery")
    @ApiResponse(responseCode = "200", description = "The numbers")
    @GetMapping("/api/admin/stats")
    ResponseEntity<HttpResponse> stats() {
        return HttpResponse.ok("Stats loaded", adminService.stats());
    }
}
