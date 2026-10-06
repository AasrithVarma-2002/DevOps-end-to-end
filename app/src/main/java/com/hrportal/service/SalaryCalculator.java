package com.hrportal.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Turns a monthly salary and the days worked into a payslip's figures. No database access, so
 * it is easy to test and explain.
 * <pre>
 *   earned gross  = monthly salary × paid days / days in the month
 *   basic         = 50% of earned gross
 *   HRA           = 20% of earned gross
 *   special       = the rest
 *   PF            = 12% of basic, basic capped at 15,000 (so at most 1,800)
 *   prof. tax     = 200 when earned gross ≥ 15,000, else 0
 *   net pay       = earned gross − PF − professional tax
 * </pre>
 * Income tax (TDS) depends on each employee's declarations and is not calculated here.
 */
public final class SalaryCalculator {

    public static final BigDecimal BASIC_SHARE = new BigDecimal("0.50");
    public static final BigDecimal HRA_SHARE = new BigDecimal("0.20");
    public static final BigDecimal PF_RATE = new BigDecimal("0.12");
    public static final BigDecimal PF_WAGE_CEILING = new BigDecimal("15000");
    public static final BigDecimal PROFESSIONAL_TAX = new BigDecimal("200");
    public static final BigDecimal PROFESSIONAL_TAX_THRESHOLD = new BigDecimal("15000");

    private SalaryCalculator() {
    }

    public record Breakdown(BigDecimal grossEarned, BigDecimal basic, BigDecimal hra, BigDecimal special,
                            BigDecimal providentFund, BigDecimal professionalTax, BigDecimal totalDeductions,
                            BigDecimal netPay) {
    }

    public static Breakdown calculate(BigDecimal monthlySalary, BigDecimal paidDays, int daysInMonth) {
        if (monthlySalary == null || monthlySalary.signum() < 0) {
            throw new IllegalArgumentException("Monthly salary must be zero or more");
        }
        if (daysInMonth <= 0 || paidDays.signum() < 0 || paidDays.compareTo(BigDecimal.valueOf(daysInMonth)) > 0) {
            throw new IllegalArgumentException("Paid days must be between 0 and " + daysInMonth);
        }
        BigDecimal gross = money(monthlySalary.multiply(paidDays).divide(BigDecimal.valueOf(daysInMonth), 10, RoundingMode.HALF_UP));
        BigDecimal basic = money(gross.multiply(BASIC_SHARE));
        BigDecimal hra = money(gross.multiply(HRA_SHARE));
        BigDecimal special = gross.subtract(basic).subtract(hra);

        BigDecimal pf = money(basic.min(PF_WAGE_CEILING).multiply(PF_RATE));
        BigDecimal pt = gross.compareTo(PROFESSIONAL_TAX_THRESHOLD) >= 0 ? PROFESSIONAL_TAX : BigDecimal.ZERO;
        BigDecimal deductions = pf.add(pt);
        return new Breakdown(gross, basic, hra, special, pf, money(pt), money(deductions), gross.subtract(deductions));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
