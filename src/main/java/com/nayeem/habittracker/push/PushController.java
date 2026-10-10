package com.nayeem.habittracker.push;

import com.nayeem.habittracker.common.AppConstants;
import com.nayeem.habittracker.common.response.HttpResponse;
import com.nayeem.habittracker.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Push")
@SecurityRequirement(name = AppConstants.JWT_TOKEN)
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
class PushController {

    private final PushService pushService;

    @Operation(summary = "Whether push is on, and the key to subscribe with")
    @ApiResponse(responseCode = "200", description = "enabled, publicKey (null when off)")
    @GetMapping("/public-key")
    ResponseEntity<HttpResponse> publicKey() {
        return HttpResponse.ok("Push key loaded", pushService.publicKey());
    }

    @Operation(summary = "Save this browser's push subscription",
            description = "The body is the browser's PushSubscription.toJSON(). Only the browsers' own push services "
                    + "are accepted (FCM, Mozilla, Apple, Windows). Saving the same browser again is fine.")
    @ApiResponse(responseCode = "201", description = "Saved")
    @ApiResponse(responseCode = "400", description = "Not a browser push subscription (PUSH_SUBSCRIPTION_INVALID)")
    @PostMapping("/subscriptions")
    ResponseEntity<HttpResponse> subscribe(@AuthenticationPrincipal AuthUser user,
                                           @Valid @RequestBody SubscribeRequest request) {
        pushService.subscribe(user.id(), request);
        return HttpResponse.of(HttpStatus.CREATED, "Push subscription saved", null);
    }

    @Operation(summary = "Forget this browser's push subscription (on sign-out, or when notifications are turned off)")
    @ApiResponse(responseCode = "204", description = "Forgotten; also when it wasn't saved")
    @DeleteMapping("/subscriptions")
    ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal AuthUser user,
                                     @Valid @RequestBody UnsubscribeRequest request) {
        pushService.unsubscribe(user.id(), request.getEndpoint());
        return ResponseEntity.noContent().build();
    }
}
