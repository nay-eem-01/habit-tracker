package com.nayeem.habittracker.user;

/** USER for everyone; ADMIN is set by hand in the database (V18) and opens {@code /api/admin/**}. */
public enum Role {
    USER,
    ADMIN
}
