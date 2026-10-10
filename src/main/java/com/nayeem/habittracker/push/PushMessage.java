package com.nayeem.habittracker.push;

/** A push to all of a user's browsers, published as an event and sent after the transaction commits. */
public record PushMessage(Long userId, String title, String body, String url) {
}
