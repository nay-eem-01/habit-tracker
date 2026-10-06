package com.nayeem.habittracker.level;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Groups of levels (PLAN.md §11.3). */
@Getter
@RequiredArgsConstructor
public enum Tier {

    BRONZE(1),
    SILVER(5),
    GOLD(10),
    PLATINUM(20),
    DIAMOND(35);

    /** The first level in this tier. */
    private final int fromLevel;

    static Tier of(int level) {
        Tier tier = BRONZE;
        for (Tier t : values()) {
            if (level >= t.fromLevel) {
                tier = t;
            }
        }
        return tier;
    }
}
