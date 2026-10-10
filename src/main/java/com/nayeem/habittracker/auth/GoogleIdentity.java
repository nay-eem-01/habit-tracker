package com.nayeem.habittracker.auth;

/** Who a verified Google ID token says the user is. {@code toString} leaves the email out (logs). */
record GoogleIdentity(String subject, String email, boolean emailVerified, String name) {

    @Override
    public String toString() {
        return "GoogleIdentity[subject=" + subject + "]";
    }
}
