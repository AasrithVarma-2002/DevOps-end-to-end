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
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "resignations")
public class Resignation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResignationStatus status = ResignationStatus.SUBMITTED;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "requested_last_day", nullable = false)
    private LocalDate requestedLastDay;

    @Column(name = "last_working_day")
    private LocalDate lastWorkingDay;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "decided_by", length = 120)
    private String decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "hr_note", length = 500)
    private String hrNote;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public Resignation() {
    }

    public Resignation(Employee employee, String reason, LocalDate requestedLastDay, LocalDateTime submittedAt) {
        this.employee = employee;
        this.reason = reason;
        this.requestedLastDay = requestedLastDay;
        this.submittedAt = submittedAt;
    }

    public void decide(ResignationStatus decision, LocalDate lastWorkingDay, String by, LocalDateTime at, String note) {
        this.status = decision;
        this.lastWorkingDay = lastWorkingDay;
        this.decidedBy = by;
        this.decidedAt = at;
        this.hrNote = note;
    }

    public void withdraw(LocalDateTime at) {
        this.status = ResignationStatus.WITHDRAWN;
        this.decidedAt = at;
    }

    public void complete(LocalDateTime at) {
        this.status = ResignationStatus.COMPLETED;
        this.completedAt = at;
    }

    public Long getEmployeeId() {
        return employee.getId();
    }

    public String getEmployeeName() {
        return employee.getFullName();
    }

    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public ResignationStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public LocalDate getRequestedLastDay() { return requestedLastDay; }
    public LocalDate getLastWorkingDay() { return lastWorkingDay; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public String getDecidedBy() { return decidedBy; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public String getHrNote() { return hrNote; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
