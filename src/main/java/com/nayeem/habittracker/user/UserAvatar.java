package com.nayeem.habittracker.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A user's profile picture: a 256 px square JPEG made by {@link AvatarImage}. Keyed by the user's id. */
@Getter
@Setter
@Entity
@Table(name = "user_avatars")
class UserAvatar {

    @Id
    private Long userId;

    @Column(nullable = false)
    private byte[] content;
}
