package com.nayeem.habittracker.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Package-private: other features go through {@link UserService}. */
interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);
}
