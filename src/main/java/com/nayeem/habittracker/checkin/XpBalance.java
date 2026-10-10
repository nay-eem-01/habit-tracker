package com.nayeem.habittracker.checkin;

/** What a user can still spend on rest days: XP ever earned minus XP spent (PLAN.md §3.4). The level feature knows. */
public interface XpBalance {

    long balance(Long userId);
}
