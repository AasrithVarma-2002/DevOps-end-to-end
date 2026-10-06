package com.hrportal.service;

import com.hrportal.domain.Employee;
import com.hrportal.domain.LeaveRequest;
import com.hrportal.domain.LeaveType;
import com.hrportal.domain.PayrollRun;
import com.hrportal.domain.PayrollStatus;
import com.hrportal.domain.Payslip;
import com.hrportal.domain.Role;
import com.hrportal.repository.EmployeeRepository;
import com.hrportal.repository.LeaveRequestRepository;
import com.hrportal.repository.PayrollRunRepository;
import com.hrportal.repository.PayslipRepository;
import com.hrportal.security.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly payroll.
 * <pre>
 *   HR creates the run for a month ─► DRAFT (payslips calculated, HR can review and recalculate)
 *                                  ─► FINALIZED (PDFs stored, employees notified, locked)
 * </pre>
 * Loss of pay: approved unpaid leave, plus the days of the month before joining or after the
 * last working day. Salary is pro-rated over calendar days.
 */
@Service
@Transactional
public class PayrollService {

    public static final String PDF = "application/pdf";

    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;
    private final EmployeeRepository employees;
    private final LeaveRequestRepository leaveRequests;
    private final HolidayService holidays;
    private final PayslipPdfGenerator pdfGenerator;
    private final DocumentStorage storage;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CurrentUser currentUser;
    private final Clock clock;

    public PayrollService(PayrollRunRepository runs, PayslipRepository payslips, EmployeeRepository employees,
                          LeaveRequestRepository leaveRequests, HolidayService holidays,
                          PayslipPdfGenerator pdfGenerator, DocumentStorage storage,
                          NotificationService notifications, AuditService audit, CurrentUser currentUser, Clock clock) {
        this.runs = runs;
        this.payslips = payslips;
        this.employees = employees;
        this.leaveRequests = leaveRequests;
        this.holidays = holidays;
        this.pdfGenerator = pdfGenerator;
        this.storage = storage;
        this.notifications = notifications;
        this.audit = audit;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /** Totals for the run list and the run page. */
    public record RunSummary(PayrollRun run, int employees, BigDecimal totalGross, BigDecimal totalNet) {
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<RunSummary> runs() {
        return runs.findAllByOrderByMonthDesc().stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public PayrollRun get(Long runId) {
        return runs.findById(runId).orElseThrow(() -> new NotFoundException("Payroll run " + runId + " not found"));
    }

    @Transactional(readOnly = true)
    public RunSummary summary(Long runId) {
        return summary(get(runId));
    }

    @Transactional(readOnly = true)
    public List<Payslip> payslips(Long runId) {
        return payslips.findByRun(runId);
    }

    /** The employee's own payslips; drafts stay hidden until HR finalizes the month. */
    @Transactional(readOnly = true)
    public List<Payslip> myPayslips(Employee employee) {
        return payslips.findByEmployeeAndStatus(employee.getId(), PayrollStatus.FINALIZED);
    }

    /**
     * The payslip PDF. Employees get their own finalized payslips; HR gets any, and for a draft
     * run a preview is generated on the fly (nothing is stored).
     */
    @Transactional(readOnly = true)
    public byte[] pdf(Long payslipId) {
        Payslip p = payslips.findById(payslipId).orElseThrow(() -> new NotFoundException("Payslip not found"));
        boolean isHr = currentUser.hasAtLeast(Role.HR_ADMIN);
        boolean isOwner = p.getEmployeeId().equals(currentUser.principal().map(u -> u.getEmployeeId()).orElse(null));
        if (!isHr && !(isOwner && p.getStatus() == PayrollStatus.FINALIZED)) {
            throw new AccessDeniedException("You can only download your own payslips");
        }
        return p.isDocumentReady() ? storage.get(p.getDocumentKey()) : pdfGenerator.generate(p);
    }

    // ------------------------------------------------------------------ run lifecycle

    public PayrollRun create(YearMonth month) {
        if (month == null) {
            throw new BusinessRuleException("Choose the month to run payroll for");
        }
        if (month.isAfter(YearMonth.now(clock))) {
            throw new BusinessRuleException("Payroll can't be run for a future month");
        }
        if (runs.existsByMonth(month)) {
            throw new BusinessRuleException("Payroll for " + label(month) + " already exists");
        }
        PayrollRun run = runs.save(new PayrollRun(month, currentUser.username(), LocalDateTime.now(clock)));
        int count = calculate(run);
        audit.record("PAYROLL_CREATED", "PayrollRun", run.getId(), label(month) + ": " + count + " payslip(s)");
        return run;
    }

    /** Throws away the draft payslips and calculates again (e.g. after a salary or leave change). */
    public PayrollRun recalculate(Long runId) {
        PayrollRun run = requireDraft(runId);
        payslips.deleteByRun(runId);
        payslips.flush();
        int count = calculate(run);
        audit.record("PAYROLL_RECALCULATED", "PayrollRun", runId, label(run.getMonth()) + ": " + count + " payslip(s)");
        return run;
    }

    /** Locks the run, stores each payslip PDF and tells the employees. */
    public PayrollRun finalizeRun(Long runId) {
        PayrollRun run = requireDraft(runId);
        List<Payslip> slips = payslips.findByRun(runId);
        if (slips.isEmpty()) {
            throw new BusinessRuleException("There are no payslips in this run");
        }
        run.setStatus(PayrollStatus.FINALIZED);
        run.setFinalizedBy(currentUser.username());
        run.setFinalizedAt(LocalDateTime.now(clock));
        for (Payslip p : slips) {
            String key = "payslips/" + run.getMonth() + "/" + p.getEmployeeCode() + "-" + p.getId() + ".pdf";
            storage.put(key, pdfGenerator.generate(p), PDF);
            p.setDocumentKey(key);
            notifications.notifyEmployee(p.getEmployee(), "Your payslip for " + label(run.getMonth()) + " is ready",
                    "/payslips");
        }
        audit.record("PAYROLL_FINALIZED", "PayrollRun", runId, label(run.getMonth()) + ": " + slips.size()
                + " payslip(s), net " + total(slips, Payslip::getNetPay).toPlainString());
        return run;
    }

    // ------------------------------------------------------------------ calculation

    private int calculate(PayrollRun run) {
        YearMonth month = run.getMonth();
        List<Employee> staff = employees.findEmployedBetween(month.atDay(1), month.atEndOfMonth());
        for (Employee e : staff) {
            payslips.save(payslipFor(run, e));
        }
        return staff.size();
    }

    private Payslip payslipFor(PayrollRun run, Employee e) {
        YearMonth month = run.getMonth();
        int daysInMonth = month.lengthOfMonth();
        LocalDate from = max(e.getJoiningDate(), month.atDay(1));
        LocalDate to = e.getLastWorkingDay() == null ? month.atEndOfMonth() : min(e.getLastWorkingDay(), month.atEndOfMonth());
        long employedDays = ChronoUnit.DAYS.between(from, to) + 1;

        BigDecimal lop = BigDecimal.valueOf(daysInMonth - employedDays).add(unpaidLeaveDays(e, from, to));
        BigDecimal paid = BigDecimal.valueOf(daysInMonth).subtract(lop).max(BigDecimal.ZERO);
        var b = SalaryCalculator.calculate(e.getSalary(), paid, daysInMonth);

        Payslip p = new Payslip(run, e);
        p.setMonthlySalary(e.getSalary());
        p.setDaysInMonth(daysInMonth);
        p.setPaidDays(paid.setScale(1));
        p.setLopDays(lop.setScale(1));
        p.setBasic(b.basic());
        p.setHra(b.hra());
        p.setSpecialAllowance(b.special());
        p.setGrossEarned(b.grossEarned());
        p.setProvidentFund(b.providentFund());
        p.setProfessionalTax(b.professionalTax());
        p.setTotalDeductions(b.totalDeductions());
        p.setNetPay(b.netPay());
        return p;
    }

    /** Working days of approved unpaid leave inside the range (a half day counts 0.5). */
    private BigDecimal unpaidLeaveDays(Employee e, LocalDate from, LocalDate to) {
        BigDecimal days = BigDecimal.ZERO;
        for (LeaveRequest l : leaveRequests.findApprovedOfTypeBetween(e.getId(), LeaveType.UNPAID, from, to)) {
            if (l.isHalfDay()) {
                days = days.add(holidays.isWorkingDay(l.getStartDate()) ? new BigDecimal("0.5") : BigDecimal.ZERO);
            } else {
                days = days.add(holidays.workingDays(max(l.getStartDate(), from), min(l.getEndDate(), to)));
            }
        }
        return days;
    }

    // ------------------------------------------------------------------ helpers

    private PayrollRun requireDraft(Long runId) {
        PayrollRun run = get(runId);
        if (!run.isDraft()) {
            throw new BusinessRuleException("Payroll for " + label(run.getMonth()) + " is already finalized");
        }
        return run;
    }

    private RunSummary summary(PayrollRun run) {
        List<Payslip> slips = payslips.findByRun(run.getId());
        return new RunSummary(run, slips.size(), total(slips, Payslip::getGrossEarned), total(slips, Payslip::getNetPay));
    }

    private static BigDecimal total(List<Payslip> slips, java.util.function.Function<Payslip, BigDecimal> field) {
        return slips.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** "March 2026". */
    public static String label(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear();
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
