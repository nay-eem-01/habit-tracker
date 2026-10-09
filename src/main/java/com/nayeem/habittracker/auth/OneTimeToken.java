package com.nayeem.habittracker.auth;

import com.nayeem.habittracker.common.AuditModel;
import com.nayeem.habittracker.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A one-time emailed link: "forgot password" or "verify your email". Like a refresh token, only its
 * SHA-256 is stored, so a leaked table doesn't hand out working links. Single use, short-lived.
 */
@Getter
@Setter
@Entity
@Table(name = "one_time_tokens")
public class OneTimeToken extends AuditModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private TokenPurpose purpose;

    /** Hex SHA-256 of the token sent by email; never the token itself. */
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    /** When it was used — or retired by a newer one; null while it still works. */
    private Instant usedAt;

    boolean isUsableAt(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }
}
