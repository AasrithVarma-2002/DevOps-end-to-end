package com.hrportal.domain;

import java.math.BigDecimal;

/** Leave types and their yearly allowance in working days. */
public enum LeaveType {
    ANNUAL("Annual", new BigDecimal("20"), true),
    SICK("Sick", new BigDecimal("10"), false),
    CASUAL("Casual", new BigDecimal("6"), false),
    UNPAID("Unpaid", null, false),
    MATERNITY("Maternity", new BigDecimal("180"), false),
    PATERNITY("Paternity", new BigDecimal("10"), false);

    private final String label;
    private final BigDecimal yearlyAllowance;
    private final boolean proRated;

    LeaveType(String label, BigDecimal yearlyAllowance, boolean proRated) {
        this.label = label;
        this.yearlyAllowance = yearlyAllowance;
        this.proRated = proRated;
    }

    public String getLabel() {
        return label;
    }

    /** Null means unlimited (no balance is kept). */
    public BigDecimal getYearlyAllowance() {
        return yearlyAllowance;
    }

    public boolean isLimited() {
        return yearlyAllowance != null;
    }

    /** Pro-rated types are reduced for employees who join part-way through the year. */
    public boolean isProRated() {
        return proRated;
    }
}
