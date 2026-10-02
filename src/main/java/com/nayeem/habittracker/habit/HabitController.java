package com.nayeem.habittracker.habit;

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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@Tag(name = "Habits")
@SecurityRequirement(name = AppConstants.JWT_TOKEN)
@RestController
@RequestMapping("/api/habits")
@RequiredArgsConstructor
class HabitController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "name");

    private final HabitService habitService;

    @Operation(summary = "Create a habit")
    @ApiResponse(responseCode = "201", description = "Created; Location points at the habit")
    @ApiResponse(responseCode = "400", description = "Invalid fields, or a schedule that doesn't fit the frequency type (HABIT_INVALID_FREQUENCY)")
    @PostMapping
    ResponseEntity<HttpResponse> create(@AuthenticationPrincipal AuthUser user,
                                        @Valid @RequestBody HabitRequest request) {
        HabitResponse habit = habitService.create(user.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION, "/api/habits/" + habit.id())
                .body(HttpResponse.of(HttpStatus.CREATED, "Habit created", habit).getBody());
    }

    @Operation(summary = "One of my habits")
    @ApiResponse(responseCode = "200", description = "The habit")
    @ApiResponse(responseCode = "404", description = "No such habit, or not yours (HABIT_NOT_FOUND)")
    @GetMapping("/{id}")
    ResponseEntity<HttpResponse> get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return HttpResponse.ok("Habit loaded", habitService.get(user.id(), id));
    }

    @Operation(summary = "Replace a habit's details and schedule")
    @ApiResponse(responseCode = "200", description = "The updated habit")
    @ApiResponse(responseCode = "400", description = "Invalid fields or schedule")
    @ApiResponse(responseCode = "404", description = "No such habit, or not yours (HABIT_NOT_FOUND)")
    @PutMapping("/{id}")
    ResponseEntity<HttpResponse> update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                        @Valid @RequestBody HabitRequest request) {
        return HttpResponse.ok("Habit updated", habitService.update(user.id(), id, request));
    }

    @Operation(summary = "Archive a habit (the soft delete); its history is kept")
    @ApiResponse(responseCode = "200", description = "The archived habit; archiving twice is fine")
    @ApiResponse(responseCode = "404", description = "No such habit, or not yours (HABIT_NOT_FOUND)")
    @PostMapping("/{id}/archive")
    ResponseEntity<HttpResponse> archive(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return HttpResponse.ok("Habit archived", habitService.setArchived(user.id(), id, true));
    }

    @Operation(summary = "Bring an archived habit back")
    @ApiResponse(responseCode = "200", description = "The habit")
    @ApiResponse(responseCode = "404", description = "No such habit, or not yours (HABIT_NOT_FOUND)")
    @PostMapping("/{id}/unarchive")
    ResponseEntity<HttpResponse> unarchive(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return HttpResponse.ok("Habit restored", habitService.setArchived(user.id(), id, false));
    }

    @Operation(summary = "Link a habit to one of my active goals, or change the link's target days")
    @ApiResponse(responseCode = "200", description = "The habit, with goalId and goalTargetDays")
    @ApiResponse(responseCode = "400", description = "Invalid fields")
    @ApiResponse(responseCode = "404", description = "No such habit or goal, or not yours (HABIT_NOT_FOUND, GOAL_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "The habit is archived (HABIT_ARCHIVED) or the goal isn't active (GOAL_NOT_ACTIVE)")
    @PutMapping("/{id}/goal")
    ResponseEntity<HttpResponse> linkGoal(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                          @Valid @RequestBody GoalLinkRequest request) {
        return HttpResponse.ok("Habit linked to goal", habitService.linkGoal(user.id(), id, request));
    }

    @Operation(summary = "Take a habit off its goal")
    @ApiResponse(responseCode = "200", description = "The habit; unlinking an unlinked habit is fine")
    @ApiResponse(responseCode = "404", description = "No such habit, or not yours (HABIT_NOT_FOUND)")
    @DeleteMapping("/{id}/goal")
    ResponseEntity<HttpResponse> unlinkGoal(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return HttpResponse.ok("Habit unlinked from goal", habitService.unlinkGoal(user.id(), id));
    }

    @Operation(summary = "My habits, newest first by default")
    @ApiResponse(responseCode = "200", description = "A page of habits")
    @GetMapping
    ResponseEntity<HttpResponse> list(
            @AuthenticationPrincipal AuthUser user,
            @Parameter(description = "true for the archived ones") @RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(defaultValue = PageRequests.DEFAULT_PAGE) int page,
            @Parameter(description = "1-100") @RequestParam(defaultValue = PageRequests.DEFAULT_SIZE) int size,
            @Parameter(description = "createdAt or name") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "asc or desc") @RequestParam(defaultValue = "desc") String sortDir) {
        var pageable = PageRequests.of(page, size, sortBy, sortDir, SORTABLE);
        return HttpResponse.ok("Habits loaded", habitService.list(user.id(), archived, pageable));
    }
}
