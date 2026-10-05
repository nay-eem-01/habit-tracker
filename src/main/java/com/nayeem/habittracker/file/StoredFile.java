package com.nayeem.habittracker.file;

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

/**
 * An uploaded file's metadata; the bytes live in {@link FileStorage} under {@link #storageKey}
 * (PLAN.md §13.3). Kept apart from the feature that uses it, but always owned by one user.
 */
@Getter
@Setter
@Entity
@Table(name = "stored_files", indexes = @Index(name = "idx_stored_files_user_id", columnList = "user_id"))
public class StoredFile extends AuditModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    /** Random UUID — never the user's file name, so it can't point outside the storage. */
    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private String storageKey;

    /** The name the user uploaded it as, cleaned: shown and offered on download, never a path. */
    @Column(nullable = false, length = 255)
    private String originalName;

    /** Detected from the bytes and on the allowlist ({@link FileType}), not the client's claim. */
    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    /** Hex SHA-256 of the bytes, to spot corruption or duplicates later. */
    @Column(nullable = false, length = 64)
    private String sha256;
}
