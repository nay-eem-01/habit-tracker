package com.nayeem.habittracker.security;

import com.nayeem.habittracker.user.User;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The signed-in user as Spring Security sees it. Holds ids, not the entity, so nothing lazy leaks
 * out of the persistence context. No roles in M0–M1: every user is the same kind of user.
 * {@code toString} leaves the hash out.
 */
public record AuthUser(Long id, String email, String passwordHash) implements UserDetails {

    public static AuthUser from(User user) {
        return new AuthUser(user.getId(), user.getEmail(), user.getPasswordHash());
    }

    @Override
    @NonNull
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    @NonNull
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    @NonNull
    public String toString() {
        return "AuthUser[id=" + id + "]";
    }
}
