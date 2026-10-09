package com.nayeem.habittracker.user;

import com.nayeem.habittracker.common.AuditModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A person using the app, whichever way they sign in (plan §2.1).
 * The {@code habits} relation from the plan is added with the Habit entity in Phase 3.
 */
@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends AuditModel {

    /** Stored trimmed and lower-cased by {@link UserService}, so lookups are exact matches. */
    @Column(nullable = false, unique = true, length = 320)
    private String email;

    /** BCrypt hash. Null for a Google-only account that never set a password. */
    @Column(length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider authProvider;

    /** Google's {@code sub} claim; null for LOCAL accounts. */
    @Column(length = 255)
    private String providerId;

    /** When the user proved they own the address (an emailed link, or a password reset); null until then. */
    private Instant emailVerifiedAt;

    /** IANA zone id, e.g. {@code Asia/Dhaka}. Decides what "today" is for check-ins (PLAN.md §8 Q4). */
    @Column(nullable = false, length = 64)
    private String timezone = UserService.DEFAULT_TIMEZONE;
}
