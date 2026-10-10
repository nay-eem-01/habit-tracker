package com.nayeem.habittracker.push;

import io.swagger.v3.oas.annotations.media.Schema;

public record PushKeyResponse(
        @Schema(description = "False: don't offer notifications") boolean enabled,
        @Schema(description = "applicationServerKey for pushManager.subscribe(), base64url") String publicKey) {
}
