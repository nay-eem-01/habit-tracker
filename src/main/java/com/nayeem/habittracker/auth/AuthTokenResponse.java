package com.nayeem.habittracker.auth;

import com.nayeem.habittracker.user.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/** The access token and who it belongs to. The refresh token never appears in a body (2.2: cookie). */
public record AuthTokenResponse(
        String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Seconds until the access token expires", example = "900") long expiresIn,
        UserResponse user) {

    static AuthTokenResponse bearer(String accessToken, long expiresIn, UserResponse user) {
        return new AuthTokenResponse(accessToken, "Bearer", expiresIn, user);
    }

    @Override
    public String toString() {
        return "AuthTokenResponse[accessToken=***, user=" + user.id() + "]";
    }
}
