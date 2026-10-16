package com.nayeem.habittracker.user;

import java.time.Instant;

/**
 * What a client may see about a user. Never the password hash or provider id.
 * {@code avatarUpdatedAt}: null without a profile picture; otherwise fetch GET /api/me/avatar, and use this
 * as the cache key.
 */
public record UserResponse(Long id, String email, String name, AuthProvider authProvider, String timezone,
                           boolean emailVerified, boolean marketingEmails, Plan plan, Role role,
                           Instant avatarUpdatedAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getAuthProvider(),
                user.getTimezone(), user.getEmailVerifiedAt() != null, user.isMarketingEmails(),
                user.getPlan(), user.getRole(), user.getAvatarUpdatedAt());
    }
}
