package com.nayeem.habittracker.push;

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

/** A browser the user allowed notifications in: where to send (the push service's URL) and its keys. */
@Getter
@Setter
@Entity
@Table(name = "push_subscriptions", indexes = @Index(name = "idx_push_subscriptions_user_id", columnList = "user_id"))
public class PushSubscription extends AuditModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The push service URL — only well-known push services are accepted ({@link PushService}). */
    @Column(nullable = false, unique = true, length = 1000)
    private String endpoint;

    /** The browser's P-256 public key, base64url. */
    @Column(nullable = false, length = 100)
    private String p256dh;

    /** The browser's auth secret, base64url. */
    @Column(nullable = false, length = 50)
    private String auth;
}
