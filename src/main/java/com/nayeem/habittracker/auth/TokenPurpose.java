package com.nayeem.habittracker.auth;

/** What a {@link OneTimeToken} is for; a token only works for its own purpose. */
public enum TokenPurpose {
    PASSWORD_RESET,
    EMAIL_VERIFICATION
}
