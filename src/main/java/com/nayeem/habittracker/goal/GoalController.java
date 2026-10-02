package com.nayeem.habittracker.goal;

import com.nayeem.habittracker.common.AppConstants;
import com.nayeem.habittracker.common.pagination.PageRequests;
import com.nayeem.habittracker.common.response.HttpResponse;
import com.nayeem.habittracker.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@Tag(name = "Goals")
@SecurityRequirement(name = AppConstants.JWT_TOKEN)
@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
class GoalController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "title", "targetDate");

    private final GoalService goalService;

    @Operation(summary = "Create a goal")
    @ApiResponse(responseCode = "201", description = "Created; Location points at the goal")
    @ApiResponse(responseCode = "400", description = "Invalid fields")
    @PostMapping
    ResponseEntity<HttpResponse> create(@AuthenticationPrincipal AuthUser user,
                                        @Valid @RequestBody GoalRequest request) {
        GoalResponse goal = goalService.create(user.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION, "/api/goals/" + goal.id())
                .body(HttpResponse.of(HttpStatus.CREATED, "Goal created", goal).getBody());
    }

    @Operation(summary = "One of my goals")
    @ApiResponse(responseCode = "200", description = "The goal")
    @ApiResponse(responseCode = "404", description = "No such goal, or not yours (GOAL_NOT_FOUND)")
    @GetMapping("/{id}")
    ResponseEntity<HttpResponse> get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return HttpResponse.ok("Goal loaded", goalService.get(user.id(), id));
    }

    @Operation(summary = "Replace a goal's title, description and target date")
    @ApiResponse(responseCode = "200", description = "The updated goal")
    @ApiResponse(responseCode = "400", description = "Invalid fields")
    @ApiResponse(responseCode = "404", description = "No such goal, or not yours (GOAL_NOT_FOUND)")
    @PutMapping("/{id}")
    ResponseEntity<HttpResponse> update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                        @Valid @RequestBody GoalRequest request) {
        return HttpResponse.ok("Goal updated", goalService.update(user.id(), id, request));
    }

    @Operation(summary = "My goals, newest first by default")
    @ApiResponse(responseCode = "200", description = "A page of goals")
    @GetMapping
    ResponseEntity<HttpResponse> list(
            @AuthenticationPrincipal AuthUser user,
            @Parameter(description = "ACTIVE, ACHIEVED or ABANDONED; omit for all") @RequestParam(required = false) GoalStatus status,
            @RequestParam(defaultValue = PageRequests.DEFAULT_PAGE) int page,
            @Parameter(description = "1-100") @RequestParam(defaultValue = PageRequests.DEFAULT_SIZE) int size,
            @Parameter(description = "createdAt, title or targetDate") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "asc or desc") @RequestParam(defaultValue = "desc") String sortDir) {
        var pageable = PageRequests.of(page, size, sortBy, sortDir, SORTABLE);
        return HttpResponse.ok("Goals loaded", goalService.list(user.id(), status, pageable));
    }
}
