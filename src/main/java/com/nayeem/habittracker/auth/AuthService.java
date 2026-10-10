package com.nayeem.habittracker.auth;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import com.nayeem.habittracker.security.JwtService;
import com.nayeem.habittracker.security.RateLimiter;
import com.nayeem.habittracker.user.User;
import com.nayeem.habittracker.user.UserResponse;
import com.nayeem.habittracker.user.UserService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** Wrong passwords allowed per account (or per unknown email) in {@link #FAILURE_WINDOW}. */
    static final int MAX_FAILURES = 5;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final RateLimiter rateLimiter;

    /** Hash of a random value nobody knows; only used to spend equal time on a failed lookup. */
    private String dummyHash;

    @PostConstruct
    void initDummyHash() {
        dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** Creates a LOCAL account and signs it straight in. */
    @Transactional
    public AuthResult register(RegisterRequest request) {
        String hash = passwordEncoder.encode(request.getPassword());
        User user = userService.createLocalUser(request.getEmail(), hash, request.getName(), request.getTimezone());
        emailVerificationService.sendLink(user);
        return issueTokens(user);
    }

    /**
     * One answer for every failure — unknown email, wrong password, Google-only account — so the
     * response doesn't reveal which accounts exist.
     */
    @Transactional
    public AuthResult login(LoginRequest request) {
        String failures = "login-fail:" + request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (rateLimiter.isExhausted(failures, MAX_FAILURES)) {
            log.info("Login refused: too many failures for this email");
            throw new ApplicationException(ErrorCode.RATE_LIMITED);
        }
        Optional<User> found = userService.findByEmail(request.getEmail());
        String hash = found.map(User::getPasswordHash).orElse(null);
        if (hash == null) {
            // Spend the same BCrypt time as a real check, so timing doesn't tell either.
            passwordEncoder.matches(request.getPassword(), dummyHash);
            log.info("Login failed: {}", found.isPresent() ? "account has no password (Google)" : "no such account");
            rateLimiter.tryAcquire(failures, MAX_FAILURES, FAILURE_WINDOW);
            throw new ApplicationException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        User user = found.get();
        if (!passwordEncoder.matches(request.getPassword(), hash)) {
            log.info("Login failed for user {}: wrong password", user.getId());
            rateLimiter.tryAcquire(failures, MAX_FAILURES, FAILURE_WINDOW);
            throw new ApplicationException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        log.info("User {} logged in", user.getId());
        return issueTokens(user);
    }

    /**
     * Sign in with Google (PLAN.md §3.2): the account with this Google id; else the account with this
     * email, now linked; else a new account. An account whose email was never verified loses its
     * password and sessions when linked — its owner may not be the one who set them.
     */
    @Transactional
    public AuthResult googleSignIn(GoogleSignInRequest request) {
        GoogleIdentity google = googleIdTokenVerifier.verify(request.getIdToken());
        if (!google.emailVerified() || google.email() == null) {
            throw new ApplicationException(ErrorCode.AUTH_GOOGLE_EMAIL_UNVERIFIED);
        }
        User user = userService.findByGoogleSubject(google.subject())
                .or(() -> userService.findByEmail(google.email()).map(existing -> linkGoogle(existing, google)))
                .orElseGet(() -> userService.createGoogleUser(google.email(), google.name(), google.subject(),
                        request.getTimezone()));
        log.info("User {} signed in with Google", user.getId());
        return issueTokens(user);
    }

    private User linkGoogle(User existing, GoogleIdentity google) {
        boolean unverified = existing.getEmailVerifiedAt() == null;
        if (unverified) {
            refreshTokenService.revokeAll(existing.getId());
            passwordResetService.retireAll(existing.getId());
        }
        return userService.linkGoogle(existing.getId(), google.subject(),
                unverified && existing.getPasswordHash() != null);
    }

    /** Rotates the refresh token: the presented one dies, a new pair is issued. */
    @Transactional(noRollbackFor = ApplicationException.class)
    public AuthResult refresh(String refreshToken) {
        Long userId = refreshTokenService.consume(refreshToken);
        return issueTokens(userService.getById(userId));
    }

    /**
     * Emails a reset link when the address has an account. The caller answers the same either way,
     * so the endpoint doesn't reveal which emails are registered.
     */
    @Transactional
    public void forgotPassword(String email) {
        userService.findByEmail(email).ifPresentOrElse(passwordResetService::sendLink,
                () -> log.info("Password reset requested for an email with no account"));
    }

    /**
     * Sets the new password from a reset link, signs the user out everywhere, then signs them in
     * here. A Google-only account gains a password this way.
     */
    @Transactional
    public AuthResult resetPassword(ResetPasswordRequest request) {
        User user = passwordResetService.consume(request.getToken());
        userService.updatePasswordHash(user.getId(), passwordEncoder.encode(request.getNewPassword()));
        userService.markEmailVerified(user.getId());   // the emailed link proves they own the address
        int revoked = refreshTokenService.revokeAll(user.getId());
        log.info("Password reset for user {}; {} session(s) signed out", user.getId(), revoked);
        return issueTokens(user);
    }

    /**
     * Changes the password of a signed-in user: the current one must match. Signs out the other
     * sessions, retires reset links, and returns a fresh pair for this one. A Google-only account
     * has no current password to check; until Google sign-in (2.3) can confirm it's them, it sets
     * its first password through "forgot password".
     */
    @Transactional
    public AuthResult changePassword(Long userId, ChangePasswordRequest request) {
        User user = userService.getById(userId);
        if (user.getPasswordHash() == null) {
            throw new ApplicationException(ErrorCode.AUTH_PASSWORD_NOT_SET);
        }
        confirmPassword(user, request.getCurrentPassword());
        userService.updatePasswordHash(userId, passwordEncoder.encode(request.getNewPassword()));
        int revoked = refreshTokenService.revokeAll(userId);
        passwordResetService.retireAll(userId);
        log.info("Password changed for user {}; {} session(s) signed out", userId, revoked);
        return issueTokens(user);
    }

    /**
     * For a sensitive change by a signed-in user (new password, deleting the account): the password
     * must match — 400 {@code AUTH_WRONG_PASSWORD}, and after 5 misses 429 for 15 minutes. An account
     * without a password has nothing to check.
     */
    public void confirmPassword(User user, String password) {
        if (user.getPasswordHash() == null) {
            return;
        }
        String failures = "password-confirm-fail:" + user.getId();
        if (rateLimiter.isExhausted(failures, MAX_FAILURES)) {
            throw new ApplicationException(ErrorCode.RATE_LIMITED);
        }
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            log.info("Password check for user {} failed", user.getId());
            rateLimiter.tryAcquire(failures, MAX_FAILURES, FAILURE_WINDOW);
            throw new ApplicationException(ErrorCode.AUTH_WRONG_PASSWORD);
        }
    }

    /** Sends a new confirmation link to the signed-in user. */
    public void resendVerification(Long userId) {
        emailVerificationService.sendLink(userService.getById(userId));
    }

    public void verifyEmail(String token) {
        emailVerificationService.verify(token);
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    public UserResponse me(Long userId) {
        return UserResponse.from(userService.getById(userId));
    }

    private AuthResult issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        AuthTokenResponse body =
                AuthTokenResponse.bearer(accessToken, jwtService.accessTokenTtlSeconds(), UserResponse.from(user));
        return new AuthResult(body, refreshTokenService.issue(user));
    }
}
