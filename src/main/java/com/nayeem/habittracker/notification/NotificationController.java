package com.nayeem.habittracker.notification;

import com.nayeem.habittracker.common.AppConstants;
import com.nayeem.habittracker.common.pagination.PageRequests;
import com.nayeem.habittracker.common.response.HttpResponse;
import com.nayeem.habittracker.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notifications")
@SecurityRequirement(name = AppConstants.JWT_TOKEN)
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "My notifications, unread first, then newest first")
    @ApiResponse(responseCode = "200", description = "A page of notifications")
    @GetMapping
    ResponseEntity<HttpResponse> list(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(defaultValue = PageRequests.DEFAULT_PAGE) int page,
            @Parameter(description = "1-100") @RequestParam(defaultValue = PageRequests.DEFAULT_SIZE) int size) {
        return HttpResponse.ok("Notifications loaded",
                notificationService.list(user.id(), PageRequests.unsorted(page, size)));
    }

    @Operation(summary = "How many notifications are unread (cheap to poll)")
    @ApiResponse(responseCode = "200", description = "The unread count")
    @GetMapping("/unread-count")
    ResponseEntity<HttpResponse> unreadCount(@AuthenticationPrincipal AuthUser user) {
        return HttpResponse.ok("Unread count loaded", notificationService.unreadCount(user.id()));
    }

    @Operation(summary = "Mark one notification read; doing it twice is fine")
    @ApiResponse(responseCode = "200", description = "The notification")
    @ApiResponse(responseCode = "404", description = "No such notification, or not yours (NOTIFICATION_NOT_FOUND)")
    @PostMapping("/{id}/read")
    ResponseEntity<HttpResponse> markRead(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return HttpResponse.ok("Notification marked read", notificationService.markRead(user.id(), id));
    }

    @Operation(summary = "Mark all my notifications read")
    @ApiResponse(responseCode = "200", description = "How many were unread")
    @PostMapping("/read-all")
    ResponseEntity<HttpResponse> markAllRead(@AuthenticationPrincipal AuthUser user) {
        return HttpResponse.ok("Notifications marked read", notificationService.markAllRead(user.id()));
    }
}
