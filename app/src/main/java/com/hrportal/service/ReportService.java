package com.hrportal.service;

import com.hrportal.domain.Employee;
import com.hrportal.domain.PayrollRun;
import com.hrportal.domain.Payslip;
import com.hrportal.repository.EmployeeRepository;
import com.hrportal.repository.PayrollRunRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CSV exports for HR. Each covers everyone employed in the period, including people who left. */
@Service
@Transactional
public class ReportService {

    private final EmployeeRepository employees;
    private final AttendanceService attendance;
    private final LeaveService leave;
    private final PayrollRunRepository runs;
    private final PayrollService payroll;
    private final AuditService audit;

    public ReportService(EmployeeRepository employees, AttendanceService attendance, LeaveService leave,
                         PayrollRunRepository runs, PayrollService payroll, AuditService audit) {
        this.employees = employees;
        this.attendance = attendance;
        this.leave = leave;
        this.runs = runs;
        this.payroll = payroll;
        this.audit = audit;
    }

    /** One row per employee: the month's attendance totals. */
    public String attendance(YearMonth month) {
        Csv csv = new Csv().row("Employee code", "Name", "Department", "Present days", "Half days", "On leave",
                "Absent days", "Hours worked");
        for (Employee e : employees.findEmployedBetween(month.atDay(1), month.atEndOfMonth())) {
            var s = attendance.month(e, month).summary();
            csv.row(e.getEmployeeCode(), e.getFullName(), e.getDepartment().getName(), s.present(), s.halfDays(),
                    s.onLeave(), s.absent(), hours(s.minutesWorked()));
        }
        return csv.toString();
    }

    /** One row per employee and leave type: allowance, used, pending and available for the year. */
    public String leave(int year) {
        Csv csv = new Csv().row("Employee code", "Name", "Department", "Leave type", "Allocated", "Used", "Pending",
                "Available");
        for (Employee e : employees.findEmployedBetween(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))) {
            for (var b : leave.balances(e, year)) {
                csv.row(e.getEmployeeCode(), e.getFullName(), e.getDepartment().getName(), b.type().getLabel(),
                        b.allocated(), b.used(), b.pending(), b.available());
            }
        }
        return csv.toString();
    }

    /** The payroll register: every payslip of the month's run. Audited, because it shows salaries. */
    public String payrollRegister(YearMonth month) {
        PayrollRun run = runs.findByMonth(month)
                .orElseThrow(() -> new NotFoundException("No payroll for " + PayrollService.label(month)));
        Csv csv = new Csv().row("Employee code", "Name", "Department", "Monthly salary", "Days in month",
                "Paid days", "Loss of pay days", "Basic", "HRA", "Special allowance", "Gross earned",
                "Provident fund", "Professional tax", "Total deductions", "Net pay", "Run status");
        for (Payslip p : payroll.payslips(run.getId())) {
            csv.row(p.getEmployeeCode(), p.getEmployeeName(), p.getEmployee().getDepartment().getName(),
                    money(p.getMonthlySalary()), p.getDaysInMonth(), p.getPaidDays(), p.getLopDays(), money(p.getBasic()),
                    money(p.getHra()), money(p.getSpecialAllowance()), money(p.getGrossEarned()),
                    money(p.getProvidentFund()), money(p.getProfessionalTax()), money(p.getTotalDeductions()),
                    money(p.getNetPay()), run.getStatus().getLabel());
        }
        audit.record("PAYROLL_REGISTER_EXPORTED", "PayrollRun", run.getId(), PayrollService.label(month));
        return csv.toString();
    }

    /** Amounts always with 2 decimals: 100000 → 100000.00 */
    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    /** 570 minutes → 9.50 */
    private static BigDecimal hours(int minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
