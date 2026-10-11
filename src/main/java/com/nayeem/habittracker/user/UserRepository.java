package com.nayeem.habittracker.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Package-private: other features go through {@link UserService}. */
interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByProviderId(String providerId);

    /** Spring Data escapes {@code %} and {@code _} in {@code part}, so they match literally. */
    Page<User> findByEmailContaining(String part, Pageable pageable);
}
