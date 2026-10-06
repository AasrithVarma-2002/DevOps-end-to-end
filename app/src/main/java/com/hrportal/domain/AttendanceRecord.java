package com.hrportal.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One employee's check-in and check-out for one day. */
@Entity
@Table(name = "attendance_records")
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "check_in", nullable = false)
    private LocalDateTime checkIn;

    @Column(name = "check_out")
    private LocalDateTime checkOut;

    /** Filled in at check-out. */
    @Column(name = "minutes_worked")
    private Integer minutesWorked;

    @Column(name = "corrected_by", length = 120)
    private String correctedBy;

    @Column(length = 300)
    private String note;

    public AttendanceRecord() {
    }

    public AttendanceRecord(Employee employee, LocalDate workDate, LocalDateTime checkIn) {
        this.employee = employee;
        this.workDate = workDate;
        this.checkIn = checkIn;
    }

    /** Sets the check-out time and the minutes worked. */
    public void checkOutAt(LocalDateTime time) {
        this.checkOut = time;
        this.minutesWorked = (int) Duration.between(checkIn, time).toMinutes();
    }

    public boolean isOpen() {
        return checkOut == null;
    }

    /** "7h 45m", or null while still checked in. */
    public String getWorkedLabel() {
        return minutesWorked == null ? null : minutesWorked / 60 + "h " + minutesWorked % 60 + "m";
    }

    public Long getEmployeeId() {
        return employee.getId();
    }

    public String getEmployeeName() {
        return employee.getFullName();
    }

    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public LocalDate getWorkDate() { return workDate; }
    public LocalDateTime getCheckIn() { return checkIn; }
    public void setCheckIn(LocalDateTime checkIn) { this.checkIn = checkIn; }
    public LocalDateTime getCheckOut() { return checkOut; }
    public Integer getMinutesWorked() { return minutesWorked; }
    public String getCorrectedBy() { return correctedBy; }
    public void setCorrectedBy(String correctedBy) { this.correctedBy = correctedBy; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
