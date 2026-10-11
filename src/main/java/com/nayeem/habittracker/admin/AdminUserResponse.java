package com.nayeem.habittracker.admin;

import com.nayeem.habittracker.user.AuthProvider;
import com.nayeem.habittracker.user.Plan;
import com.nayeem.habittracker.user.Role;
import com.nayeem.habittracker.user.User;

import java.time.Instant;

/** A user as an admin sees them: the account, never its content. */
public record AdminUserResponse(Long id, String email, String name, AuthProvider authProvider, boolean emailVerified,
                                Plan plan, Role role, Instant suspendedAt, Instant createdAt) {

    static AdminUserResponse from(User user) {
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getName(), user.getAuthProvider(),
                user.getEmailVerifiedAt() != null, user.getPlan(), user.getRole(), user.getSuspendedAt(),
                user.getCreatedAt());
    }
}
