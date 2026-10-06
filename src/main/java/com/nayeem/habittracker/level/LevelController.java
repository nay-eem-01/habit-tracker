package com.nayeem.habittracker.level;

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

@Tag(name = "Levels")
@SecurityRequirement(name = AppConstants.JWT_TOKEN)
@RestController
@RequiredArgsConstructor
class LevelController {

    private final LevelService levelService;

    @Operation(summary = "My XP, level and tier",
            description = "Computed from all my habits (archived too) and achieved goals; XP is never lost when "
                    + "a streak breaks. +10 per done day that counts for the streak, +5 more while the streak "
                    + "is at least 7 days (1 week), +50/+200/+500/+1500 at 7/30/100/365 days "
                    + "(1/4/14/52 weeks), +500 per achieved goal.")
    @ApiResponse(responseCode = "200", description = "The level")
    @GetMapping("/api/me/level")
    ResponseEntity<HttpResponse> level(@AuthenticationPrincipal AuthUser user) {
        return HttpResponse.ok("Level loaded", levelService.level(user.id()));
    }
}
