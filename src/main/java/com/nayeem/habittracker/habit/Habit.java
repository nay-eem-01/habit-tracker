package com.nayeem.habittracker.habit;

import com.nayeem.habittracker.common.AuditModel;
import com.nayeem.habittracker.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Something a user wants to do regularly (plan §2.2). Soft-deleted by archiving; the logs and
 * streak history stay. {@code User} has no {@code habits} collection: nothing needs to walk from a
 * user to all their habits in memory, and queries do it better.
 */
@Getter
@Setter
@Entity
@Table(name = "habits", indexes = @Index(name = "idx_habits_user_id", columnList = "user_id"))
public class Habit extends AuditModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 50)
    private String category;

    /** Set together with the config through {@link #schedule}, so the two always agree. */
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FrequencyType frequencyType;

    @Setter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private FrequencyConfig frequencyConfig;

    /** Completions a day needs to count as done, e.g. 8 for "drink water 8×". */
    @Column(nullable = false)
    private int targetCount = 1;

    @Column(nullable = false)
    private boolean archived;

    // TODO: link to Goal entity, M2 (PLAN.md §11)

    /** Sets how often the habit is due; rejects a config that doesn't fit the type (400). */
    public void schedule(FrequencyType type, FrequencyConfig config) {
        this.frequencyConfig = FrequencyConfig.normalize(type, config);
        this.frequencyType = type;
    }
}
