package com.nayeem.habittracker.auth;

/**
 * What a sign-in produces: the body for the client, and the raw refresh token, which the
 * controller puts in an httpOnly cookie and never in the body.
 */
record AuthResult(AuthTokenResponse body, String refreshToken) {

    @Override
    public String toString() {
        return "AuthResult[" + body + ", refreshToken=***]";
    }
}
