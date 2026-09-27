package com.hrportal.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Yearly allowance and usage for one limited leave type. Unpaid leave has no balance row. */
@Entity
@Table(name = "leave_balances")
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "leave_year", nullable = false)
    private int year;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 20)
    private LeaveType type;

    @Column(nullable = false, precision = 5, scale = 1)
    private BigDecimal allocated;

    @Column(nullable = false, precision = 5, scale = 1)
    private BigDecimal used = BigDecimal.ZERO;

    public LeaveBalance() {
    }

    public LeaveBalance(Employee employee, int year, LeaveType type, BigDecimal allocated) {
        this.employee = employee;
        this.year = year;
        this.type = type;
        this.allocated = allocated;
    }

    public BigDecimal getRemaining() {
        return allocated.subtract(used);
    }

    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public int getYear() { return year; }
    public LeaveType getType() { return type; }
    public BigDecimal getAllocated() { return allocated; }
    public void setAllocated(BigDecimal allocated) { this.allocated = allocated; }
    public BigDecimal getUsed() { return used; }
    public void setUsed(BigDecimal used) { this.used = used; }
}
