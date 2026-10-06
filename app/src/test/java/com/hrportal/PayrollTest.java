package com.hrportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hrportal.domain.Department;
import com.hrportal.domain.Employee;
import com.hrportal.domain.LeaveType;
import com.hrportal.domain.PayrollStatus;
import com.hrportal.domain.Payslip;
import com.hrportal.domain.Role;
import com.hrportal.service.BusinessRuleException;
import com.hrportal.service.EmployeeService;
import com.hrportal.service.LeaveService;
import com.hrportal.service.NotificationService;
import com.hrportal.service.PayrollService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** Payroll runs: who is paid, loss of pay, the draft → finalized lifecycle and access. "Today" is 4 Mar 2026. */
@SpringBootTest
@Import(TestClockConfig.class)
@Transactional
class PayrollTest {

    static final YearMonth FEB = YearMonth.of(2026, 2);
    static final YearMonth MAR = YearMonth.of(2026, 3);

    @Autowired TestData data;
    @Autowired PayrollService payroll;
    @Autowired LeaveService leave;
    @Autowired EmployeeService employees;
    @Autowired NotificationService notifications;

    Department dept;
    Employee hr;
    Employee manager;
    Employee employee;   // salary 100,000, joined 2020

    @BeforeEach
    void setUp() {
        dept = data.department();
        hr = data.hire("Hr", Role.HR_ADMIN, null, dept);
        manager = data.hire("Manager", Role.MANAGER, null, dept);
        employee = data.hire("Employee", Role.EMPLOYEE, manager, dept);
        data.loginAs(hr);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Payslip slip(Long runId, Employee e) {
        return payroll.payslips(runId).stream().filter(p -> p.getEmployeeId().equals(e.getId())).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ who is paid and how much

    @Test
    void fullMonthIsPaidInFull() {
        var run = payroll.create(FEB);
        Payslip p = slip(run.getId(), employee);
        assertThat(p.getDaysInMonth()).isEqualTo(28);
        assertThat(p.getPaidDays()).isEqualByComparingTo("28");
        assertThat(p.getLopDays()).isEqualByComparingTo("0");
        assertThat(p.getGrossEarned()).isEqualByComparingTo("100000");
        assertThat(p.getNetPay()).isEqualByComparingTo("98000");
    }

    @Test
    void joinersArePaidFromTheirJoiningDate() {
        Employee joiner = data.hire("Joiner", Role.EMPLOYEE, manager, dept, LocalDate.of(2026, 2, 16));
        data.loginAs(hr);
        Payslip p = slip(payroll.create(FEB).getId(), joiner);
        assertThat(p.getPaidDays()).isEqualByComparingTo("13");   // 16–28 Feb
        assertThat(p.getLopDays()).isEqualByComparingTo("15");
        assertThat(p.getGrossEarned()).isEqualByComparingTo("46428.57");
    }

    @Test
    void approvedUnpaidLeaveIsLossOfPay() {
        data.loginAs(employee);
        var unpaid = leave.apply(employee, new LeaveService.Application(LeaveType.UNPAID, LocalDate.of(2026, 3, 9),
                LocalDate.of(2026, 3, 10), false, "Family function"));
        leave.managerDecision(unpaid.getId(), manager, true, null);
        data.loginAs(hr);

        Payslip p = slip(payroll.create(MAR).getId(), employee);
        assertThat(p.getLopDays()).isEqualByComparingTo("2");
        assertThat(p.getPaidDays()).isEqualByComparingTo("29");
        assertThat(p.getGrossEarned()).isEqualByComparingTo("93548.39");
    }

    @Test
    void paidLeaveDoesNotReducePay() {
        data.loginAs(employee);
        var annual = leave.apply(employee, new LeaveService.Application(LeaveType.ANNUAL, LocalDate.of(2026, 3, 9),
                LocalDate.of(2026, 3, 10), false, null));
        leave.managerDecision(annual.getId(), manager, true, null);
        data.loginAs(hr);
        assertThat(slip(payroll.create(MAR).getId(), employee).getLopDays()).isEqualByComparingTo("0");
    }

    @Test
    void leaversArePaidUpToTheirLastWorkingDayAndThenDropOut() {
        employees.offboard(employee.getId(), LocalDate.of(2026, 2, 13), "Resignation");
        Payslip p = slip(payroll.create(FEB).getId(), employee);
        assertThat(p.getPaidDays()).isEqualByComparingTo("13");
        assertThat(slip(payroll.create(MAR).getId(), employee)).isNull();
    }

    // ------------------------------------------------------------------ lifecycle

    @Test
    void oneRunPerMonthAndNotInTheFuture() {
        payroll.create(FEB);
        assertThatThrownBy(() -> payroll.create(FEB))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already exists");
        assertThatThrownBy(() -> payroll.create(YearMonth.of(2026, 4)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("future");
    }

    @Test
    void draftCanBeRecalculatedAfterASalaryChange() {
        var run = payroll.create(FEB);
        employee.setSalary(new BigDecimal("50000"));
        payroll.recalculate(run.getId());
        Payslip p = slip(run.getId(), employee);
        assertThat(p.getMonthlySalary()).isEqualByComparingTo("50000");
        assertThat(p.getGrossEarned()).isEqualByComparingTo("50000");
    }

    @Test
    void finalizingStoresPdfsPublishesAndLocksTheRun() {
        var run = payroll.create(FEB);
        assertThat(payroll.myPayslips(employee)).isEmpty();   // drafts are hidden from employees

        payroll.finalizeRun(run.getId());

        assertThat(run.getStatus()).isEqualTo(PayrollStatus.FINALIZED);
        assertThat(run.getFinalizedBy()).isEqualTo(data.account(hr).getUsername());
        Payslip p = slip(run.getId(), employee);
        assertThat(p.getDocumentKey()).startsWith("payslips/2026-02/").endsWith(".pdf");
        assertThat(payroll.myPayslips(employee)).extracting(Payslip::getId).containsExactly(p.getId());
        assertThat(notifications.recent(data.account(employee).getId()))
                .anySatisfy(n -> assertThat(n.getMessage()).contains("payslip for February 2026"));

        assertThatThrownBy(() -> payroll.recalculate(run.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already finalized");
        assertThatThrownBy(() -> payroll.finalizeRun(run.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already finalized");
    }

    // ------------------------------------------------------------------ PDFs and access

    @Test
    void employeesDownloadOnlyTheirOwnFinalizedPayslips() {
        Employee colleague = data.hire("Colleague", Role.EMPLOYEE, manager, dept);
        data.loginAs(hr);
        var run = payroll.create(FEB);
        Payslip mine = slip(run.getId(), employee);

        data.loginAs(employee);
        assertThatThrownBy(() -> payroll.pdf(mine.getId())).isInstanceOf(AccessDeniedException.class); // still a draft

        data.loginAs(hr);
        assertThat(isPdf(payroll.pdf(mine.getId()))).isTrue();   // HR preview of the draft
        payroll.finalizeRun(run.getId());

        data.loginAs(employee);
        assertThat(isPdf(payroll.pdf(mine.getId()))).isTrue();
        data.loginAs(colleague);
        assertThatThrownBy(() -> payroll.pdf(mine.getId())).isInstanceOf(AccessDeniedException.class);
    }

    private static boolean isPdf(byte[] bytes) {
        return new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-");
    }
}
