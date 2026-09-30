package com.nayeem.habittracker.user;

/** How the account signs in. One {@code users} table serves both (plan §1.5). */
public enum AuthProvider {
    LOCAL,
    GOOGLE
}
