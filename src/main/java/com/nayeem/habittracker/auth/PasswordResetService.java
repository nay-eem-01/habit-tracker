package com.nayeem.habittracker.auth;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import com.nayeem.habittracker.configs.AppProperties;
import com.nayeem.habittracker.notification.OutgoingNotification;
import com.nayeem.habittracker.security.SecurityProperties;
import com.nayeem.habittracker.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * "Forgot password" links ({@link OneTimeTokenService}), valid for {@code app.security.password-reset.ttl}.
 * The email goes out after the transaction commits. Logs the user id only — never the link or the address.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class PasswordResetService {

    private final OneTimeTokenService tokens;
    private final SecurityProperties securityProperties;
    private final AppProperties appProperties;
    private final ApplicationEventPublisher events;

    /** Creates a link and queues its email — unless the user asked within the last minute or too often this hour. */
    @Transactional
    public void sendLink(User user) {
        SecurityProperties.PasswordReset settings = securityProperties.getPasswordReset();
        tokens.issue(user, TokenPurpose.PASSWORD_RESET, settings.getTtl(), settings.getMaxPerHour())
                .ifPresentOrElse(raw -> {
                    events.publishEvent(new OutgoingNotification(user.getId(), user.getEmail(), subject(user),
                            body(user, raw)));
                    log.info("Password reset link sent to user {}", user.getId());
                }, () -> log.info("Password reset for user {} not sent: too many requests", user.getId()));
    }

    /** Uses a link: returns its user and retires every link the user still has. */
    @Transactional
    public User consume(String raw) {
        return tokens.consume(raw, TokenPurpose.PASSWORD_RESET)
                .orElseThrow(() -> new ApplicationException(ErrorCode.AUTH_INVALID_RESET_TOKEN));
    }

    /** After a password change: links sent before it stop working. */
    @Transactional
    public void retireAll(Long userId) {
        tokens.retireAll(userId, TokenPurpose.PASSWORD_RESET);
    }

    private String subject(User user) {
        String action = user.getPasswordHash() == null ? "Set" : "Reset";
        return action + " your " + appProperties.getDisplayName() + " password";
    }

    private String body(User user, String raw) {
        // The token goes after '#': the browser never sends that part to a server or in a Referer.
        String link = appProperties.getFrontendUrl() + "/reset-password#token=" + raw;
        long minutes = securityProperties.getPasswordReset().getTtl().toMinutes();
        String what = user.getPasswordHash() == null
                ? "set a password for your " + appProperties.getDisplayName() + " account (you sign in with Google today)"
                : "reset the password of your " + appProperties.getDisplayName() + " account";
        return "Hi " + user.getName() + ",\n\n"
                + "Someone — hopefully you — asked to " + what + ".\n\n"
                + "Open this link within " + minutes + " minutes to choose a new password:\n"
                + link + "\n\n"
                + "The link works once. If you didn't ask for this, ignore this email: nothing changes.\n";
    }
}
