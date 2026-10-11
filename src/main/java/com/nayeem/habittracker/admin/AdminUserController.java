package com.nayeem.habittracker.admin;

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

/** Admins only (SecurityConfig); anyone else gets 404. */
@Tag(name = "Admin")
@SecurityRequirement(name = AppConstants.JWT_TOKEN)
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
class AdminUserController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "email");

    private final AdminService adminService;

    @Operation(summary = "Users, newest first by default; search by part of the email")
    @ApiResponse(responseCode = "200", description = "A page of users")
    @GetMapping
    ResponseEntity<HttpResponse> list(
            @Parameter(description = "Part of the email") @RequestParam(required = false) String q,
            @RequestParam(defaultValue = PageRequests.DEFAULT_PAGE) int page,
            @Parameter(description = "1-100") @RequestParam(defaultValue = PageRequests.DEFAULT_SIZE) int size,
            @Parameter(description = "createdAt or email") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "asc or desc") @RequestParam(defaultValue = "desc") String sortDir) {
        return HttpResponse.ok("Users loaded",
                adminService.users(q, PageRequests.of(page, size, sortBy, sortDir, SORTABLE)));
    }

    @Operation(summary = "A user's account and usage counts (never their habits, notes or links)")
    @ApiResponse(responseCode = "200", description = "The user")
    @ApiResponse(responseCode = "404", description = "No such user (USER_NOT_FOUND)")
    @GetMapping("/{id}")
    ResponseEntity<HttpResponse> get(@PathVariable Long id) {
        return HttpResponse.ok("User loaded", adminService.user(id));
    }

    @Operation(summary = "Set a user's plan (FREE or PRO)")
    @ApiResponse(responseCode = "200", description = "The updated user")
    @PutMapping("/{id}/plan")
    ResponseEntity<HttpResponse> changePlan(@AuthenticationPrincipal AuthUser admin, @PathVariable Long id,
                                            @Valid @RequestBody ChangePlanRequest request) {
        return HttpResponse.ok("Plan changed", adminService.changePlan(admin.id(), id, request.getPlan()));
    }

    @Operation(summary = "Suspend a user: no sign-in, tokens stop working, sessions end")
    @ApiResponse(responseCode = "200", description = "The updated user")
    @ApiResponse(responseCode = "409", description = "The user is an admin (ADMIN_TARGET_IS_ADMIN)")
    @PostMapping("/{id}/suspend")
    ResponseEntity<HttpResponse> suspend(@AuthenticationPrincipal AuthUser admin, @PathVariable Long id) {
        return HttpResponse.ok("User suspended", adminService.suspend(admin.id(), id));
    }

    @Operation(summary = "Lift a user's suspension")
    @ApiResponse(responseCode = "200", description = "The updated user")
    @PostMapping("/{id}/unsuspend")
    ResponseEntity<HttpResponse> unsuspend(@AuthenticationPrincipal AuthUser admin, @PathVariable Long id) {
        return HttpResponse.ok("Suspension lifted", adminService.unsuspend(admin.id(), id));
    }

    @Operation(summary = "Delete a user and everything in their account, for good")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "409", description = "The user is an admin (ADMIN_TARGET_IS_ADMIN)")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser admin, @PathVariable Long id) {
        adminService.delete(admin.id(), id);
        return ResponseEntity.noContent().build();
    }
}
