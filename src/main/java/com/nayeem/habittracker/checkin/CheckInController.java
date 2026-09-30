package com.nayeem.habittracker.checkin;

import com.nayeem.habittracker.common.AppConstants;
import com.nayeem.habittracker.common.response.HttpResponse;
import com.nayeem.habittracker.security.AuthUser;
import com.nayeem.habittracker.common.pagination.PageRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Check-ins")
@SecurityRequirement(name = AppConstants.JWT_TOKEN)
@RestController
@RequestMapping("/api/habits/{habitId}")
@RequiredArgsConstructor
class CheckInController {

    private final CheckInService checkInService;
    private final HabitProgressService habitProgressService;

    @Operation(summary = "Check in: set a day's count for a habit (today by default); repeating it is safe")
    @ApiResponse(responseCode = "200", description = "The day's log")
    @ApiResponse(responseCode = "400", description = "Future day, more than 7 days back, or before the habit existed (LOG_DATE_OUT_OF_RANGE)")
    @ApiResponse(responseCode = "404", description = "No such habit, or not yours (HABIT_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "The habit is archived (HABIT_ARCHIVED)")
    @PostMapping("/checkin")
    ResponseEntity<HttpResponse> checkIn(@AuthenticationPrincipal AuthUser user, @PathVariable Long habitId,
                                         @Valid @RequestBody(required = false) CheckInRequest request) {
        CheckInRequest body = request == null ? new CheckInRequest() : request;
        return HttpResponse.ok("Checked in", checkInService.checkIn(user.id(), habitId, body));
    }

    @Operation(summary = "A habit's check-ins between two days, newest first (default: the last 30 days)")
    @ApiResponse(responseCode = "200", description = "A page of logs")
    @ApiResponse(responseCode = "400", description = "from after to, or more than 366 days apart")
    @ApiResponse(responseCode = "404", description = "No such habit, or not yours (HABIT_NOT_FOUND)")
    @GetMapping("/logs")
    ResponseEntity<HttpResponse> logs(
            @AuthenticationPrincipal AuthUser user, @PathVariable Long habitId,
            @Parameter(description = "First day, inclusive") @RequestParam(required = false) LocalDate from,
            @Parameter(description = "Last day, inclusive; defaults to today") @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = PageRequests.DEFAULT_PAGE) int page,
            @Parameter(description = "1-100") @RequestParam(defaultValue = PageRequests.DEFAULT_SIZE) int size) {
        return HttpResponse.ok("Logs loaded", checkInService.logs(user.id(), habitId, from, to, page, size));
    }

    @Operation(summary = "The habit's current and longest streak (days, or weeks for N-times-a-week habits)")
    @ApiResponse(responseCode = "200", description = "The streak; today or this week never breaks it while in progress")
    @ApiResponse(responseCode = "404", description = "No such habit, or not yours (HABIT_NOT_FOUND)")
    @GetMapping("/streak")
    ResponseEntity<HttpResponse> streak(@AuthenticationPrincipal AuthUser user, @PathVariable Long habitId) {
        return HttpResponse.ok("Streak loaded", habitProgressService.streak(user.id(), habitId));
    }
}
