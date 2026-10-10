package com.nayeem.habittracker.user;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.SQLException;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;

/** The user feature's public entry point; other features never touch {@link UserRepository}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    public static final String DEFAULT_TIMEZONE = "UTC";

    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * Creates an email/password account.
     *
     * @param passwordHash already hashed — this service never sees a raw password
     * @param timezone     IANA zone id; blank means {@value #DEFAULT_TIMEZONE}
     */
    @Transactional
    public User createLocalUser(String email, String passwordHash, String name, String timezone) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ApplicationException(ErrorCode.USER_EMAIL_TAKEN);
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordHash);
        user.setName(name.trim());
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setTimezone(normalizeTimezone(timezone));

        try {
            // flush now so a concurrent sign-up with the same email fails here, as a 409
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Only a unique violation means the email is taken (it is the only unique column the
            // sign-up fills in). Anything else — a NOT NULL column, a stale schema — is a real
            // fault and must surface as one, not as "already registered".
            if (!isUniqueViolation(e)) {
                throw e;
            }
            throw new ApplicationException(ErrorCode.USER_EMAIL_TAKEN);
        }
        log.info("User {} registered ({})", user.getId(), AuthProvider.LOCAL);
        return user;
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(normalizeEmail(email));
    }

    /** Sets (or replaces) the password. A Google-only account gains one this way. */
    @Transactional
    public void updatePasswordHash(Long userId, String passwordHash) {
        getById(userId).setPasswordHash(passwordHash);
    }

    /** Records that the user owns their address; the first time counts. */
    @Transactional
    public void markEmailVerified(Long userId) {
        User user = getById(userId);
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(clock.instant());
        }
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));
    }

    /** PostgreSQL SQLSTATE 23505: unique_violation. */
    private static final String UNIQUE_VIOLATION = "23505";

    static boolean isUniqueViolation(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static String normalizeTimezone(String timezone) {
        if (!StringUtils.hasText(timezone)) {
            return DEFAULT_TIMEZONE;
        }
        // Region names only (Asia/Dhaka, UTC, Etc/GMT-6): PostgreSQL reads an offset like "+06:00" with
        // the sign flipped, so the reminder query would fire at the wrong hour.
        String id = timezone.trim();
        if (!ZoneId.getAvailableZoneIds().contains(id)) {
            throw new ApplicationException(ErrorCode.USER_INVALID_TIMEZONE);
        }
        return id;
    }
}
