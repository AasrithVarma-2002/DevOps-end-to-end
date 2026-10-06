package com.hrportal.domain;

/** How a day looks on the attendance calendar. Worked out from records, leave and holidays. */
public enum DayStatus {
    PRESENT("Present"),
    /** Checked out after less than {@code AttendanceService.HALF_DAY_MINUTES}. */
    HALF_DAY("Half day"),
    /** Checked in on a past day but never checked out. */
    MISSED_CHECKOUT("No check-out"),
    /** Checked in today, not yet out. */
    WORKING("Checked in"),
    ON_LEAVE("On leave"),
    HOLIDAY("Holiday"),
    WEEKEND("Weekend"),
    /** A past working day with no attendance and no approved leave. */
    ABSENT("Absent"),
    /** Today before check-in, or a future day. */
    NOT_YET("—");

    private final String label;

    DayStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
