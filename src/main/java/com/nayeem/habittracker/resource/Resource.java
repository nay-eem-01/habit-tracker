package com.nayeem.habittracker.resource;

import com.nayeem.habittracker.common.AuditModel;
import com.nayeem.habittracker.goal.Goal;
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
import lombok.Getter;
import lombok.Setter;

/**
 * Something a user keeps — a Markdown note or a link — optionally next to a goal (PLAN.md §11.2).
 * The API stores text; rendering it is the client's job. A link's address is never fetched by the
 * server.
 */
@Getter
@Setter
@Entity
@Table(name = "resources", indexes = {
        @Index(name = "idx_resources_user_id", columnList = "user_id"),
        @Index(name = "idx_resources_goal_id", columnList = "goal_id")})
public class Resource extends AuditModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    /** The goal it belongs to; null = stands alone. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id")
    private Goal goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ResourceType type;

    @Column(nullable = false, length = 200)
    private String title;

    /** Markdown for a note (required); an optional comment for a link. */
    @Column(columnDefinition = "text")
    private String body;

    /** {@code http(s)} address; required for a link, absent for a note. */
    @Column(length = 2048)
    private String url;

    @Column(nullable = false)
    private boolean pinned;
}
