package com.hrportal.domain;

public enum LeaveStatus {
    PENDING_MANAGER("Waiting for manager"),
    PENDING_HR("Waiting for HR"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    CANCELLED("Cancelled");

    private final String label;

    LeaveStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isPending() {
        return this == PENDING_MANAGER || this == PENDING_HR;
    }
}
