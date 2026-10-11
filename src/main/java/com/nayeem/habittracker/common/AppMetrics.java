package com.nayeem.habittracker.common;

/**
 * Counters the admin stats read back, tagged {@code outcome}. In memory: they start at zero with the
 * app. shortcut: lost on restart; store them if admins need history.
 */
public final class AppMetrics {

    /** Outcomes {@code sent}, {@code failed}. */
    public static final String EMAILS = "app.emails";
    /** Outcomes {@code sent}, {@code gone} (the browser dropped the subscription), {@code failed}. */
    public static final String PUSHES = "app.pushes";
    public static final String OUTCOME = "outcome";

    private AppMetrics() {
    }
}
