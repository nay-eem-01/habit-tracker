package com.nayeem.habittracker.checkin;

import com.nayeem.habittracker.common.AppConstants;
import com.nayeem.habittracker.common.response.HttpResponse;
import com.nayeem.habittracker.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Check-ins")
@SecurityRequirement(name = AppConstants.JWT_TOKEN)
@RestController
@RequestMapping("/api/habits/{habitId}")
@RequiredArgsConstructor
class CheckInController {

    private final CheckInService checkInService;

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
}
