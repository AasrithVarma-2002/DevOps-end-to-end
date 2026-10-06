package com.hrportal.domain;

/**
 * SUBMITTED ─(HR accepts)─► ACCEPTED ─(last working day passes)─► COMPLETED
 *           ├(HR declines)─► DECLINED
 *           └(employee withdraws)─► WITHDRAWN
 */
public enum ResignationStatus {
    SUBMITTED("Waiting for HR"),
    ACCEPTED("Accepted · serving notice"),
    DECLINED("Declined"),
    WITHDRAWN("Withdrawn"),
    COMPLETED("Completed · left");

    private final String label;

    ResignationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Still in progress: blocks a second resignation. */
    public boolean isOpen() {
        return this == SUBMITTED || this == ACCEPTED;
    }
}
