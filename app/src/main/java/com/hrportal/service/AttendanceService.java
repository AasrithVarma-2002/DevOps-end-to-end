package com.hrportal.service;

import com.hrportal.domain.AttendanceRecord;
import com.hrportal.domain.DayStatus;
import com.hrportal.domain.Employee;
import com.hrportal.domain.Holiday;
import com.hrportal.domain.LeaveRequest;
import com.hrportal.repository.AttendanceRecordRepository;
import com.hrportal.repository.LeaveRequestRepository;
import com.hrportal.security.CurrentUser;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily attendance.
 * <pre>
 *   check in (once a day) ─► WORKING ─(check out)─► PRESENT, or HALF_DAY if under 4 hours
 *   past day still open                           ─► MISSED_CHECKOUT (HR can correct it)
 *   past working day, no record, no leave         ─► ABSENT
 * </pre>
 * Approved leave, weekends and public holidays come from the leave module.
 */
@Service
@Transactional
public class AttendanceService {

    /** Days with fewer minutes than this count as a half day. */
    public static final int HALF_DAY_MINUTES = 4 * 60;

    private final AttendanceRecordRepository records;
    private final LeaveRequestRepository leaveRequests;
    private final HolidayService holidays;
    private final EmployeeService employees;
    private final AuditService audit;
    private final CurrentUser currentUser;
    private final Clock clock;

    public AttendanceService(AttendanceRecordRepository records, LeaveRequestRepository leaveRequests,
                             HolidayService holidays, EmployeeService employees, AuditService audit,
                             CurrentUser currentUser, Clock clock) {
        this.records = records;
        this.leaveRequests = leaveRequests;
        this.holidays = holidays;
        this.employees = employees;
        this.audit = audit;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /** One calendar day for one employee. */
    public record Day(LocalDate date, DayStatus status, AttendanceRecord record, String detail) {
    }

    /** Totals shown above the calendar and on the team page. */
    public record Summary(int present, int halfDays, int onLeave, int absent, int minutesWorked) {
        public String getHoursLabel() {
            return minutesWorked / 60 + "h " + minutesWorked % 60 + "m";
        }
    }

    public record Month(YearMonth month, List<Day> days, Summary summary) {
    }

    /** A direct report's state today, for the manager. */
    public record TeamMember(Employee employee, Day today, Summary month) {
    }

    // ------------------------------------------------------------------ check in / out

    public AttendanceRecord checkIn(Employee employee) {
        if (!employee.isActive()) {
            throw new BusinessRuleException("Employees who have left cannot check in");
        }
        LocalDate today = today();
        if (records.findByEmployee_IdAndWorkDate(employee.getId(), today).isPresent()) {
            throw new BusinessRuleException("You have already checked in today");
        }
        AttendanceRecord record = records.save(new AttendanceRecord(employee, today, now()));
        audit.record("CHECKED_IN", "AttendanceRecord", record.getId(), employee.getFullName() + " at " + time(record.getCheckIn()));
        return record;
    }

    public AttendanceRecord checkOut(Employee employee) {
        AttendanceRecord record = records.findByEmployee_IdAndWorkDate(employee.getId(), today())
                .orElseThrow(() -> new BusinessRuleException("You haven't checked in today"));
        if (!record.isOpen()) {
            throw new BusinessRuleException("You have already checked out today");
        }
        record.checkOutAt(now());
        audit.record("CHECKED_OUT", "AttendanceRecord", record.getId(),
                employee.getFullName() + " at " + time(record.getCheckOut()) + " (" + record.getWorkedLabel() + ")");
        return record;
    }

    /**
     * HR adds or fixes a past day, e.g. a forgotten check-out or a day the employee worked
     * on site without the portal. Today and future days are left to the employee.
     */
    public AttendanceRecord correct(Long employeeId, LocalDate date, LocalTime in, LocalTime out, String note) {
        if (date == null || in == null || out == null) {
            throw new BusinessRuleException("Date, check-in and check-out times are required");
        }
        if (!date.isBefore(today())) {
            throw new BusinessRuleException("Only past days can be corrected");
        }
        if (!out.isAfter(in)) {
            throw new BusinessRuleException("Check-out must be after check-in");
        }
        if (note == null || note.isBlank()) {
            throw new BusinessRuleException("Please give a reason for the correction");
        }
        Employee employee = employees.get(employeeId);
        if (date.isBefore(employee.getJoiningDate())
                || (employee.getLastWorkingDay() != null && date.isAfter(employee.getLastWorkingDay()))) {
            throw new BusinessRuleException(employee.getFullName() + " was not employed on " + date);
        }
        AttendanceRecord record = records.findByEmployee_IdAndWorkDate(employeeId, date)
                .orElseGet(() -> new AttendanceRecord(employee, date, date.atTime(in)));
        record.setCheckIn(date.atTime(in));
        record.checkOutAt(date.atTime(out));
        record.setCorrectedBy(currentUser.username());
        record.setNote(note.trim());
        records.save(record);
        audit.record("ATTENDANCE_CORRECTED", "AttendanceRecord", record.getId(),
                employee.getFullName() + " " + date + " " + in + "-" + out + ": " + note.trim());
        return record;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public AttendanceRecord todayRecord(Employee employee) {
        return records.findByEmployee_IdAndWorkDate(employee.getId(), today()).orElse(null);
    }

    @Transactional(readOnly = true)
    public Month month(Employee employee, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        Map<LocalDate, AttendanceRecord> byDate = records
                .findByEmployee_IdAndWorkDateBetweenOrderByWorkDateAsc(employee.getId(), from, to).stream()
                .collect(Collectors.toMap(AttendanceRecord::getWorkDate, Function.identity()));
        Map<LocalDate, String> holidayNames = holidaysBetween(from, to);
        Map<LocalDate, LeaveRequest> leave = leaveByDate(employee, from, to);

        List<Day> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            days.add(day(employee, d, byDate.get(d), holidayNames, leave));
        }
        return new Month(month, days, summarize(days));
    }

    /** Today and this month for each direct report of the manager. */
    @Transactional(readOnly = true)
    public List<TeamMember> team(Long managerEmployeeId) {
        YearMonth thisMonth = YearMonth.from(today());
        List<TeamMember> result = new ArrayList<>();
        for (Employee e : employees.directReports(managerEmployeeId)) {
            Month m = month(e, thisMonth);
            Day today = m.days().get(today().getDayOfMonth() - 1);
            result.add(new TeamMember(e, today, m.summary()));
        }
        return result;
    }

    /** Every active employee's status on one day, for HR. */
    @Transactional(readOnly = true)
    public List<TeamMember> everyoneOn(LocalDate date) {
        Map<Long, AttendanceRecord> byEmployee = records.findByWorkDate(date).stream()
                .collect(Collectors.toMap(AttendanceRecord::getEmployeeId, Function.identity()));
        Map<LocalDate, String> holidayNames = holidaysBetween(date, date);
        List<TeamMember> result = new ArrayList<>();
        for (Employee e : employees.active()) {
            Day d = day(e, date, byEmployee.get(e.getId()), holidayNames, leaveByDate(e, date, date));
            result.add(new TeamMember(e, d, null));
        }
        return result;
    }

    // ------------------------------------------------------------------ helpers

    private Day day(Employee employee, LocalDate date, AttendanceRecord record, Map<LocalDate, String> holidayNames,
                    Map<LocalDate, LeaveRequest> leave) {
        LocalDate today = today();
        boolean employed = !date.isBefore(employee.getJoiningDate())
                && (employee.getLastWorkingDay() == null || !date.isAfter(employee.getLastWorkingDay()));
        if (record != null) {
            if (record.isOpen()) {
                return new Day(date, date.equals(today) ? DayStatus.WORKING : DayStatus.MISSED_CHECKOUT, record,
                        "In " + time(record.getCheckIn()));
            }
            DayStatus status = record.getMinutesWorked() < HALF_DAY_MINUTES ? DayStatus.HALF_DAY : DayStatus.PRESENT;
            return new Day(date, status, record,
                    time(record.getCheckIn()) + "–" + time(record.getCheckOut()) + " · " + record.getWorkedLabel());
        }
        if (leave.containsKey(date)) {
            LeaveRequest l = leave.get(date);
            return new Day(date, DayStatus.ON_LEAVE, null, l.getType().getLabel() + (l.isHalfDay() ? " (half)" : ""));
        }
        if (holidayNames.containsKey(date)) {
            return new Day(date, DayStatus.HOLIDAY, null, holidayNames.get(date));
        }
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return new Day(date, DayStatus.WEEKEND, null, null);
        }
        if (!employed || !date.isBefore(today)) {
            return new Day(date, DayStatus.NOT_YET, null, null);
        }
        return new Day(date, DayStatus.ABSENT, null, null);
    }

    private static Summary summarize(List<Day> days) {
        int present = 0, half = 0, leave = 0, absent = 0, minutes = 0;
        for (Day d : days) {
            switch (d.status()) {
                case PRESENT, WORKING, MISSED_CHECKOUT -> present++;
                case HALF_DAY -> half++;
                case ON_LEAVE -> leave++;
                case ABSENT -> absent++;
                default -> { }
            }
            if (d.record() != null && d.record().getMinutesWorked() != null) {
                minutes += d.record().getMinutesWorked();
            }
        }
        return new Summary(present, half, leave, absent, minutes);
    }

    private Map<LocalDate, String> holidaysBetween(LocalDate from, LocalDate to) {
        Map<LocalDate, String> result = new HashMap<>();
        for (Holiday h : holidays.forRange(from, to)) {
            result.put(h.getDate(), h.getName());
        }
        return result;
    }

    private Map<LocalDate, LeaveRequest> leaveByDate(Employee employee, LocalDate from, LocalDate to) {
        Map<LocalDate, LeaveRequest> result = new HashMap<>();
        for (LeaveRequest l : leaveRequests.findApprovedBetween(employee.getId(), from, to)) {
            for (LocalDate d = l.getStartDate(); !d.isAfter(l.getEndDate()); d = d.plusDays(1)) {
                result.put(d, l);
            }
        }
        return result;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).withNano(0);
    }

    private static String time(LocalDateTime t) {
        return t.toLocalTime().withSecond(0).toString();
    }
}
