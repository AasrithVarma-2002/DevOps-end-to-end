package com.hrportal.service;

import com.hrportal.domain.Employee;
import com.hrportal.domain.Resignation;
import com.hrportal.domain.ResignationStatus;
import com.hrportal.repository.ResignationRepository;
import com.hrportal.security.CurrentUser;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resignations.
 * <pre>
 *   employee resigns ─► SUBMITTED ─(HR accepts, sets the last working day)─► ACCEPTED
 *                                 ├(HR declines, with a reason)───────────► DECLINED
 *                                 └(employee withdraws)───────────────────► WITHDRAWN
 *   ACCEPTED: the employee keeps working normally through the notice period; the night after
 *   the last working day a job offboards them (login off, leave cancelled) ─► COMPLETED
 * </pre>
 */
@Service
@Transactional
public class ResignationService {

    private static final EnumSet<ResignationStatus> OPEN = EnumSet.of(ResignationStatus.SUBMITTED, ResignationStatus.ACCEPTED);
    private static final EnumSet<ResignationStatus> CLOSED =
            EnumSet.of(ResignationStatus.DECLINED, ResignationStatus.WITHDRAWN, ResignationStatus.COMPLETED);

    private final ResignationRepository resignations;
    private final EmployeeService employees;
    private final LeaveService leave;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final int noticeDays;

    public ResignationService(ResignationRepository resignations, EmployeeService employees, LeaveService leave,
                              NotificationService notifications, AuditService audit, CurrentUser currentUser, Clock clock,
                              @Value("${app.resignation.notice-days:30}") int noticeDays) {
        this.resignations = resignations;
        this.employees = employees;
        this.leave = leave;
        this.notifications = notifications;
        this.audit = audit;
        this.currentUser = currentUser;
        this.clock = clock;
        this.noticeDays = noticeDays;
    }

    // ------------------------------------------------------------------ queries

    public int noticeDays() {
        return noticeDays;
    }

    /** The default last working day offered on the form: today plus the notice period. */
    public LocalDate defaultLastDay() {
        return today().plusDays(noticeDays);
    }

    @Transactional(readOnly = true)
    public List<Resignation> history(Long employeeId) {
        return resignations.findByEmployee(employeeId);
    }

    /** The employee's resignation that is still in progress, if any. */
    @Transactional(readOnly = true)
    public Optional<Resignation> current(Long employeeId) {
        return resignations.findByEmployee(employeeId).stream().filter(r -> r.getStatus().isOpen()).findFirst();
    }

    @Transactional(readOnly = true)
    public List<Resignation> waitingForHr() {
        return resignations.findByStatusIn(EnumSet.of(ResignationStatus.SUBMITTED));
    }

    /** Accepted and serving notice, soonest exit first. */
    @Transactional(readOnly = true)
    public List<Resignation> servingNotice() {
        return resignations.findByStatusIn(EnumSet.of(ResignationStatus.ACCEPTED)).stream()
                .sorted((a, b) -> a.getLastWorkingDay().compareTo(b.getLastWorkingDay())).toList();
    }

    @Transactional(readOnly = true)
    public List<Resignation> recentlyClosed() {
        return resignations.findRecentByStatusIn(CLOSED, PageRequest.of(0, 20));
    }

    @Transactional(readOnly = true)
    public Resignation get(Long id) {
        return resignations.findById(id).orElseThrow(() -> new NotFoundException("Resignation not found"));
    }

    // ------------------------------------------------------------------ employee

    public Resignation submit(Employee employee, LocalDate requestedLastDay, String reason) {
        if (!employee.isActive()) {
            throw new BusinessRuleException("You have already left the company");
        }
        if (current(employee.getId()).isPresent()) {
            throw new BusinessRuleException("You already have a resignation in progress");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("Please give a reason for resigning");
        }
        if (requestedLastDay == null || requestedLastDay.isBefore(today())) {
            throw new BusinessRuleException("Choose a last working day from today onwards");
        }
        Resignation r = resignations.save(new Resignation(employee, clip(reason.trim(), 1000), requestedLastDay,
                LocalDateTime.now(clock)));
        audit.record("RESIGNATION_SUBMITTED", "Resignation", r.getId(),
                employee.getFullName() + ", requested last day " + requestedLastDay);
        String message = employee.getFullName() + " has resigned. Requested last working day: " + requestedLastDay
                + (isShortNotice(r) ? " (shorter than the " + noticeDays + "-day notice period)" : "");
        notifications.notifyHr(message, "/hr/resignations");
        Employee manager = employee.getManager();
        if (manager != null && manager.isActive()) {
            notifications.notifyEmployee(manager, message, "/team");
        }
        return r;
    }

    /** Only before HR has decided. After acceptance the employee has to talk to HR. */
    public Resignation withdraw(Employee employee) {
        Resignation r = current(employee.getId())
                .orElseThrow(() -> new BusinessRuleException("You have no resignation in progress"));
        if (r.getStatus() != ResignationStatus.SUBMITTED) {
            throw new BusinessRuleException("HR has already accepted your resignation. Please talk to HR.");
        }
        r.withdraw(LocalDateTime.now(clock));
        audit.record("RESIGNATION_WITHDRAWN", "Resignation", r.getId(), employee.getFullName());
        notifications.notifyHr(employee.getFullName() + " withdrew their resignation", "/hr/resignations");
        Employee manager = employee.getManager();
        if (manager != null && manager.isActive()) {
            notifications.notifyEmployee(manager, employee.getFullName() + " withdrew their resignation", "/team");
        }
        return r;
    }

    // ------------------------------------------------------------------ HR

    /**
     * HR accepts and fixes the last working day. From now on pay, attendance and leave all stop
     * at that day: leave after it is cancelled and can't be applied for.
     */
    public Resignation accept(Long id, LocalDate lastWorkingDay, String note) {
        Resignation r = requireWaiting(id);
        Employee e = r.getEmployee();
        LocalDate day = lastWorkingDay == null ? r.getRequestedLastDay() : lastWorkingDay;
        if (day.isBefore(today())) {
            throw new BusinessRuleException("The last working day can't be in the past");
        }
        if (day.isBefore(e.getJoiningDate())) {
            throw new BusinessRuleException("The last working day can't be before the joining date");
        }
        r.decide(ResignationStatus.ACCEPTED, day, currentUser.username(), LocalDateTime.now(clock), blankToNull(note));
        e.setLastWorkingDay(day);
        int cancelled = leave.cancelAfter(e, day);
        audit.record("RESIGNATION_ACCEPTED", "Resignation", id, e.getFullName() + ", last working day " + day
                + (cancelled > 0 ? "; " + cancelled + " leave request(s) after it cancelled" : ""));
        notifications.notifyEmployee(e, "HR accepted your resignation. Your last working day is " + day
                + (r.getHrNote() == null ? "" : ". " + r.getHrNote()), "/resignation");
        Employee manager = e.getManager();
        if (manager != null && manager.isActive()) {
            notifications.notifyEmployee(manager, e.getFullName() + "'s resignation was accepted. Last working day: " + day, "/team");
        }
        return r;
    }

    public Resignation decline(Long id, String note) {
        if (note == null || note.isBlank()) {
            throw new BusinessRuleException("Tell the employee why the resignation was declined");
        }
        Resignation r = requireWaiting(id);
        r.decide(ResignationStatus.DECLINED, null, currentUser.username(), LocalDateTime.now(clock), clip(note.trim(), 500));
        audit.record("RESIGNATION_DECLINED", "Resignation", id, r.getEmployeeName() + ": " + r.getHrNote());
        notifications.notifyEmployee(r.getEmployee(), "HR declined your resignation: " + r.getHrNote(), "/resignation");
        return r;
    }

    // ------------------------------------------------------------------ nightly

    /**
     * Run every night: everyone whose last working day has passed is offboarded (login off,
     * remaining leave cancelled, team requests moved to HR). Returns how many were completed.
     */
    public int completeDue() {
        int done = 0;
        for (Resignation r : resignations.findDue(ResignationStatus.ACCEPTED, today())) {
            Employee e = r.getEmployee();
            if (e.isActive()) {
                employees.offboard(e.getId(), r.getLastWorkingDay(), "Resignation");
                notifications.notifyHr(e.getFullName() + " has left: resignation completed, last working day "
                        + r.getLastWorkingDay(), "/hr/employees/" + e.getId());
            }
            r.complete(LocalDateTime.now(clock));
            audit.recordAs("system", "RESIGNATION_COMPLETED", "Resignation", r.getId(), e.getFullName());
            done++;
        }
        // HR may have offboarded someone directly while their resignation was still open
        for (Resignation r : resignations.findByStatusIn(OPEN)) {
            if (!r.getEmployee().isActive()) {
                r.complete(LocalDateTime.now(clock));
                done++;
            }
        }
        return done;
    }

    // ------------------------------------------------------------------ helpers

    public boolean isShortNotice(Resignation r) {
        return r.getRequestedLastDay().isBefore(r.getSubmittedAt().toLocalDate().plusDays(noticeDays));
    }

    private Resignation requireWaiting(Long id) {
        Resignation r = get(id);
        if (r.getStatus() != ResignationStatus.SUBMITTED) {
            throw new BusinessRuleException("This resignation has already been handled (" + r.getStatus().getLabel() + ")");
        }
        if (r.getEmployeeId().equals(currentUser.principal().map(p -> p.getEmployeeId()).orElse(null))) {
            throw new BusinessRuleException("You can't decide your own resignation. Another HR admin has to.");
        }
        if (!r.getEmployee().isActive()) {
            throw new BusinessRuleException(r.getEmployeeName() + " has already left");
        }
        return r;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : clip(s.trim(), 500);
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
