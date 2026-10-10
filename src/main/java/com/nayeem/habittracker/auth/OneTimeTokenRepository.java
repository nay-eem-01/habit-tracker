package com.nayeem.habittracker.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, Long> {

    /** Locks the row, so two requests with the same link can't both use it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from OneTimeToken t where t.tokenHash = :tokenHash")
    Optional<OneTimeToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    long countByUserIdAndPurposeAndCreatedAtAfter(Long userId, TokenPurpose purpose, Instant after);

    /** Retires every link of the user for that purpose that still works. */
    @Modifying
    @Query("""
            update OneTimeToken t set t.usedAt = :now
            where t.user.id = :userId and t.purpose = :purpose and t.usedAt is null""")
    int retireAll(@Param("userId") Long userId, @Param("purpose") TokenPurpose purpose, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from OneTimeToken t where t.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
