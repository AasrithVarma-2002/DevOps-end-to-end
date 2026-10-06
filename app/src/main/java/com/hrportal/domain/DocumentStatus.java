package com.hrportal.domain;

/**
 * Employee uploads: PENDING ─► VERIFIED or REJECTED (by HR). Letters from HR are ISSUED.
 */
public enum DocumentStatus {
    PENDING("Waiting for HR"),
    VERIFIED("Verified"),
    REJECTED("Rejected"),
    ISSUED("Issued by HR");

    private final String label;

    DocumentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
