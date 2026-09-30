package com.nayeem.habittracker.user;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
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
            throw new ApplicationException(ErrorCode.USER_EMAIL_TAKEN);
        }
        log.info("User {} registered ({})", user.getId(), AuthProvider.LOCAL);
        return user;
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(normalizeEmail(email));
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static String normalizeTimezone(String timezone) {
        if (!StringUtils.hasText(timezone)) {
            return DEFAULT_TIMEZONE;
        }
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (DateTimeException e) {
            throw new ApplicationException(ErrorCode.USER_INVALID_TIMEZONE);
        }
    }
}
