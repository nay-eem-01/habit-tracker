package com.nayeem.habittracker.user;

import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private: other features go through {@link UserService}. */
interface UserAvatarRepository extends JpaRepository<UserAvatar, Long> {
}
