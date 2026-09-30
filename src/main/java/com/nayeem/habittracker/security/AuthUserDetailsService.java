package com.nayeem.habittracker.security;

import com.nayeem.habittracker.user.UserService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads the user a token names. Being a {@link UserDetailsService} bean also stops Boot from
 * creating its default in-memory user with a generated password.
 */
@Service
@RequiredArgsConstructor
public class AuthUserDetailsService implements UserDetailsService {

    private final UserService userService;

    @Override
    @NonNull
    public AuthUser loadUserByUsername(@NonNull String email) {
        return userService.findByEmail(email)
                .map(AuthUser::from)
                .orElseThrow(() -> new UsernameNotFoundException("No such user"));
    }
}
