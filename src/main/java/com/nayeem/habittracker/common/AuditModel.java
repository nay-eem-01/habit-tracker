package com.nayeem.habittracker.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Id and audit columns for every entity. Filled by Spring Data auditing, so there are no setters:
 * nothing else should write them. {@code createdBy}/{@code lastModifiedBy} hold the signed-in
 * user's email, or {@code SYSTEM} (see {@code JpaAuditingConfig}).
 *
 * <p>No {@code @Data}: generated equals/hashCode over mutable fields breaks entities in sets and
 * in the persistence context.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedBy
    @Column(updatable = false, length = 320)
    private String createdBy;

    @LastModifiedBy
    @Column(length = 320)
    private String lastModifiedBy;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant lastModifiedAt;
}
