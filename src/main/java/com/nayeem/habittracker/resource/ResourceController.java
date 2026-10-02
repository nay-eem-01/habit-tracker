package com.nayeem.habittracker.resource;

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

@Tag(name = "Resources")
@SecurityRequirement(name = AppConstants.JWT_TOKEN)
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
class ResourceController {

    private final ResourceService resourceService;

    @Operation(summary = "Save a note or a link")
    @ApiResponse(responseCode = "201", description = "Created; Location points at the resource")
    @ApiResponse(responseCode = "400", description = "Invalid fields, or fields that don't fit the type (RESOURCE_INVALID)")
    @ApiResponse(responseCode = "404", description = "The goal isn't yours (GOAL_NOT_FOUND)")
    @PostMapping("/resources")
    ResponseEntity<HttpResponse> create(@AuthenticationPrincipal AuthUser user,
                                        @Valid @RequestBody ResourceRequest request) {
        ResourceResponse resource = resourceService.create(user.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION, "/api/resources/" + resource.id())
                .body(HttpResponse.of(HttpStatus.CREATED, "Resource created", resource).getBody());
    }

    @Operation(summary = "One of my resources")
    @ApiResponse(responseCode = "200", description = "The resource")
    @ApiResponse(responseCode = "404", description = "No such resource, or not yours (RESOURCE_NOT_FOUND)")
    @GetMapping("/resources/{id}")
    ResponseEntity<HttpResponse> get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return HttpResponse.ok("Resource loaded", resourceService.get(user.id(), id));
    }

    @Operation(summary = "Replace a resource")
    @ApiResponse(responseCode = "200", description = "The updated resource")
    @ApiResponse(responseCode = "400", description = "Invalid fields (VALIDATION_FAILED, RESOURCE_INVALID)")
    @ApiResponse(responseCode = "404", description = "No such resource or goal, or not yours")
    @PutMapping("/resources/{id}")
    ResponseEntity<HttpResponse> update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                        @Valid @RequestBody ResourceRequest request) {
        return HttpResponse.ok("Resource updated", resourceService.update(user.id(), id, request));
    }

    @Operation(summary = "Pin a resource so it is listed first")
    @ApiResponse(responseCode = "200", description = "The resource; pinning twice is fine")
    @ApiResponse(responseCode = "404", description = "No such resource, or not yours (RESOURCE_NOT_FOUND)")
    @PostMapping("/resources/{id}/pin")
    ResponseEntity<HttpResponse> pin(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return HttpResponse.ok("Resource pinned", resourceService.setPinned(user.id(), id, true));
    }

    @Operation(summary = "Unpin a resource")
    @ApiResponse(responseCode = "200", description = "The resource")
    @ApiResponse(responseCode = "404", description = "No such resource, or not yours (RESOURCE_NOT_FOUND)")
    @PostMapping("/resources/{id}/unpin")
    ResponseEntity<HttpResponse> unpin(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return HttpResponse.ok("Resource unpinned", resourceService.setPinned(user.id(), id, false));
    }

    @Operation(summary = "Delete a resource for good")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No such resource, or not yours (RESOURCE_NOT_FOUND)")
    @DeleteMapping("/resources/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        resourceService.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "My resources, pinned first then newest")
    @ApiResponse(responseCode = "200", description = "A page of resources")
    @GetMapping("/resources")
    ResponseEntity<HttpResponse> list(
            @AuthenticationPrincipal AuthUser user,
            @Parameter(description = "Only this goal's resources") @RequestParam(required = false) Long goalId,
            @Parameter(description = "NOTE or LINK") @RequestParam(required = false) ResourceType type,
            @Parameter(description = "Title contains this text, any case") @RequestParam(required = false) String q,
            @RequestParam(defaultValue = PageRequests.DEFAULT_PAGE) int page,
            @Parameter(description = "1-100") @RequestParam(defaultValue = PageRequests.DEFAULT_SIZE) int size) {
        return HttpResponse.ok("Resources loaded", resourceService.list(user.id(), goalId, type, q, page, size));
    }
}
