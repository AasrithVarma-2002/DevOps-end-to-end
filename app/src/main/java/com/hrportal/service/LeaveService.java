package com.hrportal.service;

import com.hrportal.domain.Employee;
import com.hrportal.domain.LeaveBalance;
import com.hrportal.domain.LeaveRequest;
import com.hrportal.domain.LeaveStatus;
import com.hrportal.domain.LeaveType;
import com.hrportal.repository.LeaveBalanceRepository;
import com.hrportal.repository.LeaveRequestRepository;
import com.hrportal.security.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leave workflow.
 * <pre>
 *   apply ─► PENDING_MANAGER ─(manager approves, ≤ 5 days)─► APPROVED   (days deducted)
 *                 │            └(manager approves, > 5 days)─► PENDING_HR ─(HR approves)─► APPROVED
 *                 └─(rejected at any step)─► REJECTED
 *   Employees without an active manager go straight to PENDING_HR.
 *   Cancelling an APPROVED request refunds the days.
 * </pre>
 */
@Service
@Transactional
public class LeaveService {

    /** Leave longer than this many working days also needs HR approval. */
    public static final BigDecimal HR_APPROVAL_THRESHOLD = new BigDecimal("5");
    /** Sick leave may be recorded after the fact, up to this many days back. */
    public static final int SICK_LEAVE_BACKDATE_DAYS = 7;

    private static final EnumSet<LeaveStatus> PENDING = EnumSet.of(LeaveStatus.PENDING_MANAGER, LeaveStatus.PENDING_HR);
    private static final EnumSet<LeaveStatus> ACTIVE = EnumSet.of(
            LeaveStatus.PENDING_MANAGER, LeaveStatus.PENDING_HR, LeaveStatus.APPROVED);

    private final LeaveRequestRepository requests;
    private final LeaveBalanceRepository balances;
    private final HolidayService holidays;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CurrentUser currentUser;
    private final Clock clock;

    public LeaveService(LeaveRequestRepository requests, LeaveBalanceRepository balances, HolidayService holidays,
                        NotificationService notifications, AuditService audit, CurrentUser currentUser, Clock clock) {
        this.requests = requests;
        this.balances = balances;
        this.holidays = holidays;
        this.notifications = notifications;
        this.audit = audit;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public record Application(LeaveType type, LocalDate startDate, LocalDate endDate, boolean halfDay, String reason) {
    }

    /** Balance of one leave type as shown to the employee. */
    public record BalanceView(LeaveType type, BigDecimal allocated, BigDecimal used, BigDecimal pending,
                              BigDecimal available) {
        public boolean isLimited() {
            return type.isLimited();
        }

        /** Share of the allowance used or requested, for the progress bar. */
        public int usedPercent() {
            if (allocated == null || allocated.signum() == 0) {
                return 0;
            }
            return used.add(pending).multiply(BigDecimal.valueOf(100))
                    .divide(allocated, 0, RoundingMode.HALF_UP).min(BigDecimal.valueOf(100)).intValue();
        }
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public LeaveRequest get(Long id) {
        return requests.findById(id).orElseThrow(() -> new NotFoundException("Leave request " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> history(Long employeeId) {
        return requests.findByEmployee_IdOrderByStartDateDesc(employeeId);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> awaitingManager(Long managerEmployeeId) {
        return requests.findByManagerIdAndStatusOrderByCreatedAtAsc(managerEmployeeId, LeaveStatus.PENDING_MANAGER);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> awaitingHr() {
        return requests.findByStatusOrderByCreatedAtAsc(LeaveStatus.PENDING_HR);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> all(LeaveStatus status) {
        return requests.findAllByStatus(status);
    }

    @Transactional(readOnly = true)
    public long countAwaitingManager(Long managerEmployeeId) {
        return requests.countByManagerIdAndStatus(managerEmployeeId, LeaveStatus.PENDING_MANAGER);
    }

    @Transactional(readOnly = true)
    public long countAwaitingHr() {
        return requests.countByStatus(LeaveStatus.PENDING_HR);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> outToday() {
        return requests.findApprovedOn(today());
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> teamOutToday(Long managerEmployeeId) {
        return requests.findTeamApprovedOn(managerEmployeeId, today());
    }

    /** All leave types for the year, creating this year's balance rows on first use. */
    public List<BalanceView> balances(Employee employee, int year) {
        ensureBalances(employee, year);
        List<BalanceView> result = new ArrayList<>();
        for (LeaveType type : LeaveType.values()) {
            BigDecimal pending = requests.sumDays(employee.getId(), type, PENDING,
                    LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
            if (type.isLimited()) {
                LeaveBalance b = balances.findByEmployeeIdAndYearAndType(employee.getId(), year, type).orElseThrow();
                result.add(new BalanceView(type, b.getAllocated(), b.getUsed(), pending,
                        b.getRemaining().subtract(pending)));
            } else {
                BigDecimal used = requests.sumDays(employee.getId(), type, EnumSet.of(LeaveStatus.APPROVED),
                        LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
                result.add(new BalanceView(type, null, used, pending, null));
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ balances

    /** Creates missing balance rows for the year. Annual leave is pro-rated for mid-year joiners. */
    public void ensureBalances(Employee employee, int year) {
        for (LeaveType type : LeaveType.values()) {
            if (type.isLimited()
                    && balances.findByEmployeeIdAndYearAndType(employee.getId(), year, type).isEmpty()) {
                balances.save(new LeaveBalance(employee, year, type, allowanceFor(employee, type, year)));
            }
        }
    }

    public static BigDecimal allowanceFor(Employee employee, LeaveType type, int year) {
        BigDecimal full = type.getYearlyAllowance();
        LocalDate joined = employee.getJoiningDate();
        if (!type.isProRated() || joined.getYear() < year) {
            return full;
        }
        if (joined.getYear() > year) {
            return BigDecimal.ZERO;
        }
        int monthsWorked = 12 - joined.getMonthValue() + 1;
        // round down to the nearest half day: 20 × 10/12 = 16.67 → 16.5
        BigDecimal exact = full.multiply(BigDecimal.valueOf(monthsWorked)).divide(BigDecimal.valueOf(12), 4, RoundingMode.DOWN);
        return exact.multiply(BigDecimal.valueOf(2)).setScale(0, RoundingMode.DOWN)
                .divide(BigDecimal.valueOf(2), 1, RoundingMode.UNNECESSARY);
    }

    // ------------------------------------------------------------------ apply

    public LeaveRequest apply(Employee employee, Application app) {
        if (!employee.isActive()) {
            throw new BusinessRuleException("Employees who have left cannot apply for leave");
        }
        if (app.type() == null || app.startDate() == null || app.endDate() == null) {
            throw new BusinessRuleException("Leave type, start date and end date are required");
        }
        LocalDate start = app.startDate();
        LocalDate end = app.endDate();
        LocalDate today = today();

        if (end.isBefore(start)) {
            throw new BusinessRuleException("The end date must be on or after the start date");
        }
        if (app.halfDay() && !start.equals(end)) {
            throw new BusinessRuleException("A half day must start and end on the same date");
        }
        if (start.getYear() != end.getYear()) {
            throw new BusinessRuleException("Leave cannot cross the year end. Submit one request for each year.");
        }
        if (start.isBefore(employee.getJoiningDate())) {
            throw new BusinessRuleException("Leave cannot start before your joining date");
        }
        if (start.isBefore(today)) {
            boolean allowedBackdate = app.type() == LeaveType.SICK
                    && !start.isBefore(today.minusDays(SICK_LEAVE_BACKDATE_DAYS));
            if (!allowedBackdate) {
                throw new BusinessRuleException(app.type() == LeaveType.SICK
                        ? "Sick leave can be recorded at most " + SICK_LEAVE_BACKDATE_DAYS + " days after the fact"
                        : "Only sick leave can be applied for past dates");
            }
        }

        BigDecimal days = app.halfDay()
                ? (holidays.isWorkingDay(start) ? new BigDecimal("0.5") : BigDecimal.ZERO)
                : holidays.workingDays(start, end);
        if (days.signum() == 0) {
            throw new BusinessRuleException("The selected dates are weekends or public holidays; no leave is needed");
        }
        if (requests.existsOverlapping(employee.getId(), start, end, ACTIVE)) {
            throw new BusinessRuleException("These dates overlap another pending or approved leave request");
        }
        if (app.type().isLimited()) {
            BalanceView balance = balances(employee, start.getYear()).stream()
                    .filter(b -> b.type() == app.type()).findFirst().orElseThrow();
            if (days.compareTo(balance.available()) > 0) {
                throw new BusinessRuleException("Not enough " + app.type().getLabel().toLowerCase()
                        + " leave: you asked for " + days.stripTrailingZeros().toPlainString()
                        + " day(s) but have " + balance.available().stripTrailingZeros().toPlainString() + " available");
            }
        }

        LeaveRequest request = new LeaveRequest();
        request.setEmployee(employee);
        request.setType(app.type());
        request.setStartDate(start);
        request.setEndDate(end);
        request.setHalfDay(app.halfDay());
        request.setDays(days);
        request.setReason(blankToNull(app.reason()));
        request.setCreatedAt(LocalDateTime.now(clock));

        Employee manager = employee.getManager();
        if (manager != null && manager.isActive()) {
            request.setManager(manager);
            request.setStatus(LeaveStatus.PENDING_MANAGER);
        } else {
            request.setStatus(LeaveStatus.PENDING_HR);
        }
        requests.save(request);

        String summary = describe(request);
        if (request.getStatus() == LeaveStatus.PENDING_MANAGER) {
            notifications.notifyEmployee(manager, employee.getFullName() + " requested " + summary, "/team/approvals");
        } else {
            notifications.notifyHr(employee.getFullName() + " requested " + summary, "/hr/approvals");
        }
        audit.record("LEAVE_APPLIED", "LeaveRequest", request.getId(), employee.getFullName() + ": " + summary);
        return request;
    }

    // ------------------------------------------------------------------ decisions

    /** Step 1: the employee's manager decides. */
    public LeaveRequest managerDecision(Long requestId, Employee approver, boolean approve, String comment) {
        LeaveRequest request = get(requestId);
        if (request.getStatus() != LeaveStatus.PENDING_MANAGER) {
            throw new BusinessRuleException("This request is no longer waiting for a manager (" + request.getStatus().getLabel() + ")");
        }
        if (request.getManager() == null || !request.getManager().getId().equals(approver.getId())) {
            throw new AccessDeniedException("Only " + request.getManagerName() + " can decide this request");
        }
        request.setManagerComment(blankToNull(comment));
        request.setManagerDecidedAt(LocalDateTime.now(clock));

        if (!approve) {
            request.setStatus(LeaveStatus.REJECTED);
            notifications.notifyEmployee(request.getEmployee(), "Your " + describe(request) + " was rejected by "
                    + approver.getFullName() + commentSuffix(comment), "/leave");
            audit.record("LEAVE_REJECTED_BY_MANAGER", "LeaveRequest", requestId, auditLine(request, comment));
            return request;
        }
        if (request.getDays().compareTo(HR_APPROVAL_THRESHOLD) > 0) {
            request.setStatus(LeaveStatus.PENDING_HR);
            notifications.notifyEmployee(request.getEmployee(), "Your " + describe(request) + " was approved by "
                    + approver.getFullName() + " and is now waiting for HR", "/leave");
            notifications.notifyHr(request.getEmployeeName() + "'s " + describe(request)
                    + " needs final HR approval", "/hr/approvals");
            audit.record("LEAVE_APPROVED_BY_MANAGER", "LeaveRequest", requestId, auditLine(request, comment) + " (sent to HR)");
            return request;
        }
        audit.record("LEAVE_APPROVED_BY_MANAGER", "LeaveRequest", requestId, auditLine(request, comment));
        return finalApproval(request, approver.getFullName());
    }

    /**
     * Step 2 (or override): HR decides. HR may also decide a request still waiting for a manager,
     * e.g. when the manager is unavailable.
     */
    public LeaveRequest hrDecision(Long requestId, boolean approve, String comment) {
        LeaveRequest request = get(requestId);
        if (!request.getStatus().isPending()) {
            throw new BusinessRuleException("This request has already been decided (" + request.getStatus().getLabel() + ")");
        }
        Long actorEmployeeId = currentUser.principal().map(p -> p.getEmployeeId()).orElse(null);
        if (request.getEmployee().getId().equals(actorEmployeeId)) {
            throw new BusinessRuleException("You cannot approve or reject your own leave");
        }
        request.setHrDecidedBy(currentUser.username());
        request.setHrComment(blankToNull(comment));
        request.setHrDecidedAt(LocalDateTime.now(clock));

        if (!approve) {
            request.setStatus(LeaveStatus.REJECTED);
            notifications.notifyEmployee(request.getEmployee(), "Your " + describe(request)
                    + " was rejected by HR" + commentSuffix(comment), "/leave");
            audit.record("LEAVE_REJECTED_BY_HR", "LeaveRequest", requestId, auditLine(request, comment));
            return request;
        }
        audit.record("LEAVE_APPROVED_BY_HR", "LeaveRequest", requestId, auditLine(request, comment));
        return finalApproval(request, "HR");
    }

    private LeaveRequest finalApproval(LeaveRequest request, String approvedBy) {
        if (request.getType().isLimited()) {
            LeaveBalance balance = balanceRow(request);
            if (request.getDays().compareTo(balance.getRemaining()) > 0) {
                throw new BusinessRuleException(request.getEmployeeName() + " no longer has enough "
                        + request.getType().getLabel().toLowerCase() + " leave for this request");
            }
            balance.setUsed(balance.getUsed().add(request.getDays()));
        }
        request.setStatus(LeaveStatus.APPROVED);
        notifications.notifyEmployee(request.getEmployee(), "Your " + describe(request) + " was approved by "
                + approvedBy, "/leave");
        return request;
    }

    // ------------------------------------------------------------------ cancel

    /**
     * The employee can cancel their own pending request, or an approved one before it starts.
     * HR can cancel any pending or approved request. Approved days are refunded.
     */
    public LeaveRequest cancel(Long requestId) {
        LeaveRequest request = get(requestId);
        boolean isOwner = request.getEmployee().getId()
                .equals(currentUser.principal().map(p -> p.getEmployeeId()).orElse(null));
        boolean isHr = currentUser.hasAtLeast(com.hrportal.domain.Role.HR_ADMIN);
        if (!isOwner && !isHr) {
            throw new AccessDeniedException("You can only cancel your own leave");
        }
        if (!request.getStatus().isPending() && request.getStatus() != LeaveStatus.APPROVED) {
            throw new BusinessRuleException("Only pending or approved requests can be cancelled");
        }
        if (request.getStatus() == LeaveStatus.APPROVED && !isHr && !request.getStartDate().isAfter(today())) {
            throw new BusinessRuleException("This leave has already started. Ask HR to cancel it.");
        }
        cancelInternal(request);
        audit.record("LEAVE_CANCELLED", "LeaveRequest", requestId,
                request.getEmployeeName() + ": " + describe(request) + (isOwner ? "" : " (by HR)"));
        if (!isOwner) {
            notifications.notifyEmployee(request.getEmployee(), "Your " + describe(request) + " was cancelled by HR", "/leave");
        }
        return request;
    }

    /** Offboarding: cancels everything not yet taken. Returns how many requests were cancelled. */
    public int cancelForExit(Employee employee, LocalDate lastWorkingDay) {
        int cancelled = 0;
        for (LeaveRequest r : requests.findByEmployee_IdAndStatusIn(employee.getId(), ACTIVE)) {
            if (r.getStatus().isPending() || r.getStartDate().isAfter(lastWorkingDay)) {
                cancelInternal(r);
                cancelled++;
            }
        }
        return cancelled;
    }

    /** Offboarding a manager: their team's waiting requests move to HR so nothing gets stuck. */
    public int reroutePendingToHr(Employee departedManager) {
        List<LeaveRequest> waiting = awaitingManager(departedManager.getId());
        waiting.forEach(r -> r.setStatus(LeaveStatus.PENDING_HR));
        return waiting.size();
    }

    private void cancelInternal(LeaveRequest request) {
        if (request.getStatus() == LeaveStatus.APPROVED && request.getType().isLimited()) {
            LeaveBalance balance = balanceRow(request);
            balance.setUsed(balance.getUsed().subtract(request.getDays()).max(BigDecimal.ZERO));
        }
        request.setStatus(LeaveStatus.CANCELLED);
    }

    // ------------------------------------------------------------------ helpers

    private LeaveBalance balanceRow(LeaveRequest request) {
        int year = request.getStartDate().getYear();
        ensureBalances(request.getEmployee(), year);
        return balances.findByEmployeeIdAndYearAndType(request.getEmployee().getId(), year, request.getType())
                .orElseThrow();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    static String describe(LeaveRequest r) {
        String days = r.getDays().stripTrailingZeros().toPlainString();
        String range = r.getStartDate().equals(r.getEndDate())
                ? r.getStartDate().toString()
                : r.getStartDate() + " to " + r.getEndDate();
        return r.getType().getLabel().toLowerCase() + " leave (" + days + " day" + ("1".equals(days) ? "" : "s")
                + ", " + range + ")";
    }

    private static String auditLine(LeaveRequest r, String comment) {
        return r.getEmployeeName() + ": " + describe(r) + (comment == null || comment.isBlank() ? "" : " - \"" + comment.trim() + "\"");
    }

    private static String commentSuffix(String comment) {
        return comment == null || comment.isBlank() ? "" : ": \"" + comment.trim() + "\"";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
