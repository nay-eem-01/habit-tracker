package com.nayeem.habittracker.auth;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import com.nayeem.habittracker.security.JwtService;
import com.nayeem.habittracker.user.User;
import com.nayeem.habittracker.user.UserResponse;
import com.nayeem.habittracker.user.UserService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** Hash of a random value nobody knows; only used to spend equal time on a failed lookup. */
    private String dummyHash;

    @PostConstruct
    void initDummyHash() {
        dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** Creates a LOCAL account and signs it straight in. */
    public AuthTokenResponse register(RegisterRequest request) {
        String hash = passwordEncoder.encode(request.getPassword());
        User user = userService.createLocalUser(request.getEmail(), hash, request.getName(), request.getTimezone());
        return issueTokens(user);
    }

    /**
     * One answer for every failure — unknown email, wrong password, Google-only account — so the
     * response doesn't reveal which accounts exist.
     */
    public AuthTokenResponse login(LoginRequest request) {
        Optional<User> found = userService.findByEmail(request.getEmail());
        String hash = found.map(User::getPasswordHash).orElse(null);
        if (hash == null) {
            // Spend the same BCrypt time as a real check, so timing doesn't tell either.
            passwordEncoder.matches(request.getPassword(), dummyHash);
            log.info("Login failed: {}", found.isPresent() ? "account has no password (Google)" : "no such account");
            throw new ApplicationException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        User user = found.get();
        if (!passwordEncoder.matches(request.getPassword(), hash)) {
            log.info("Login failed for user {}: wrong password", user.getId());
            throw new ApplicationException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        log.info("User {} logged in", user.getId());
        return issueTokens(user);
    }

    public UserResponse me(Long userId) {
        return UserResponse.from(userService.getById(userId));
    }

    private AuthTokenResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        return AuthTokenResponse.bearer(accessToken, jwtService.accessTokenTtlSeconds(), UserResponse.from(user));
    }
}
