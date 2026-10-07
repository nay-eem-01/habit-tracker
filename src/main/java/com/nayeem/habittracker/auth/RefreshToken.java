package com.nayeem.habittracker.auth;

import com.nayeem.habittracker.common.AuditModel;
import com.nayeem.habittracker.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One issued refresh token (plan §2.4). Auth infrastructure, not domain data: it exists so a
 * token can be revoked on logout, rotation or password change. Only the SHA-256 hash of the
 * token is stored — a database leak must not hand out working tokens.
 */
@Getter
@Setter
@Entity
@Table(name = "refresh_tokens", indexes = @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id"))
public class RefreshToken extends AuditModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Hex SHA-256 of the raw token. */
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    /**
     * When it was used for a new pair. A rotated token presented again means someone kept a copy;
     * one revoked any other way (logout, sign out everywhere) is simply invalid.
     */
    private Instant rotatedAt;

    public boolean isUsableAt(Instant now) {
        return !revoked && now.isBefore(expiresAt);
    }
}
