package com.hrportal.domain;

/** DRAFT can be recalculated; FINALIZED is locked and visible to employees. */
public enum PayrollStatus {
    DRAFT("Draft"),
    FINALIZED("Finalized");

    private final String label;

    PayrollStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
