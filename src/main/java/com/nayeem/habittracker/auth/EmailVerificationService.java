package com.nayeem.habittracker.auth;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import com.nayeem.habittracker.configs.AppProperties;
import com.nayeem.habittracker.notification.OutgoingNotification;
import com.nayeem.habittracker.user.User;
import com.nayeem.habittracker.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * "Confirm your email" links ({@link OneTimeTokenService}), sent at sign-up and on request. A verified
 * address is what lets Google sign-in link to an account (PLAN.md §3.2). Logs ids only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class EmailVerificationService {

    static final Duration TTL = Duration.ofHours(24);
    static final int MAX_PER_HOUR = 5;

    private final OneTimeTokenService tokens;
    private final UserService userService;
    private final AppProperties appProperties;
    private final ApplicationEventPublisher events;

    /** Emails a link (after commit) unless the user asked too often; an already verified user gets none. */
    @Transactional
    public void sendLink(User user) {
        if (user.getEmailVerifiedAt() != null) {
            throw new ApplicationException(ErrorCode.AUTH_EMAIL_ALREADY_VERIFIED);
        }
        tokens.issue(user, TokenPurpose.EMAIL_VERIFICATION, TTL, MAX_PER_HOUR).ifPresentOrElse(raw -> {
            events.publishEvent(new OutgoingNotification(user.getId(), user.getEmail(),
                    "Confirm your " + appProperties.getDisplayName() + " email", body(user, raw)));
            log.info("Verification link sent to user {}", user.getId());
        }, () -> log.info("Verification link for user {} not sent: too many requests", user.getId()));
    }

    /** Uses a link and marks its user's email verified. */
    @Transactional
    public void verify(String raw) {
        User user = tokens.consume(raw, TokenPurpose.EMAIL_VERIFICATION)
                .orElseThrow(() -> new ApplicationException(ErrorCode.AUTH_INVALID_VERIFY_TOKEN));
        userService.markEmailVerified(user.getId());
        log.info("Email verified for user {}", user.getId());
    }

    private String body(User user, String raw) {
        // after '#': the browser never sends it to a server or in a Referer
        String link = appProperties.getFrontendUrl() + "/verify-email#token=" + raw;
        return "Hi " + user.getName() + ",\n\n"
                + "Confirm this is your email address for " + appProperties.getDisplayName() + ":\n"
                + link + "\n\n"
                + "The link works once, within " + TTL.toHours() + " hours. If you didn't sign up, ignore this email.\n";
    }
}
