package com.nayeem.habittracker.user;

/** What a client may see about a user. Never the password hash or provider id. */
public record UserResponse(Long id, String email, String name, AuthProvider authProvider, String timezone) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getAuthProvider(),
                user.getTimezone());
    }
}
