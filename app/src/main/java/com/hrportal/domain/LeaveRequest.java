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
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_requests")
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 20)
    private LeaveType type;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "half_day", nullable = false)
    private boolean halfDay;

    /** Working days covered (weekends and holidays excluded); 0.5 for a half day. */
    @Column(nullable = false, precision = 5, scale = 1)
    private BigDecimal days;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaveStatus status;

    /** The manager the request was routed to (the employee's manager when it was submitted). */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "manager_id")
    private Employee manager;

    @Column(name = "manager_comment", length = 500)
    private String managerComment;

    @Column(name = "manager_decided_at")
    private LocalDateTime managerDecidedAt;

    @Column(name = "hr_decided_by", length = 120)
    private String hrDecidedBy;

    @Column(name = "hr_comment", length = 500)
    private String hrComment;

    @Column(name = "hr_decided_at")
    private LocalDateTime hrDecidedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public String getEmployeeName() {
        return employee.getFullName();
    }

    public Long getEmployeeId() {
        return employee.getId();
    }

    public String getManagerName() {
        return manager == null ? null : manager.getFullName();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public LeaveType getType() { return type; }
    public void setType(LeaveType type) { this.type = type; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public boolean isHalfDay() { return halfDay; }
    public void setHalfDay(boolean halfDay) { this.halfDay = halfDay; }
    public BigDecimal getDays() { return days; }
    public void setDays(BigDecimal days) { this.days = days; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LeaveStatus getStatus() { return status; }
    public void setStatus(LeaveStatus status) { this.status = status; }
    public Employee getManager() { return manager; }
    public void setManager(Employee manager) { this.manager = manager; }
    public String getManagerComment() { return managerComment; }
    public void setManagerComment(String managerComment) { this.managerComment = managerComment; }
    public LocalDateTime getManagerDecidedAt() { return managerDecidedAt; }
    public void setManagerDecidedAt(LocalDateTime managerDecidedAt) { this.managerDecidedAt = managerDecidedAt; }
    public String getHrDecidedBy() { return hrDecidedBy; }
    public void setHrDecidedBy(String hrDecidedBy) { this.hrDecidedBy = hrDecidedBy; }
    public String getHrComment() { return hrComment; }
    public void setHrComment(String hrComment) { this.hrComment = hrComment; }
    public LocalDateTime getHrDecidedAt() { return hrDecidedAt; }
    public void setHrDecidedAt(LocalDateTime hrDecidedAt) { this.hrDecidedAt = hrDecidedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
