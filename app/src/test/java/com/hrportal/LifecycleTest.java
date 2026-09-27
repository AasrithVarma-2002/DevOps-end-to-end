package com.hrportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hrportal.domain.Department;
import com.hrportal.domain.Employee;
import com.hrportal.domain.EmployeeStatus;
import com.hrportal.domain.LeaveStatus;
import com.hrportal.domain.LeaveType;
import com.hrportal.domain.Role;
import com.hrportal.repository.UserAccountRepository;
import com.hrportal.service.AuditService;
import com.hrportal.service.BusinessRuleException;
import com.hrportal.service.EmployeeService;
import com.hrportal.service.LeaveService;
import com.hrportal.service.LeaveService.Application;
import com.hrportal.service.UserAccountService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** Onboarding, job changes, offboarding, accounts and the audit trail. */
@SpringBootTest
@Import(TestClockConfig.class)
@Transactional
class LifecycleTest {

    @Autowired TestData data;
    @Autowired EmployeeService employees;
    @Autowired LeaveService leave;
    @Autowired UserAccountService accountService;
    @Autowired UserAccountRepository accounts;
    @Autowired AuditService audit;
    @Autowired PasswordEncoder encoder;

    Department dept;
    Employee hr;

    @BeforeEach
    void setUp() {
        dept = data.department();
        hr = data.hire("Hr", Role.HR_ADMIN, null, dept);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private EmployeeService.JobDetails newHire(String code, String email, Long managerId) {
        return new EmployeeService.JobDetails(code, "Asha", "Nair", email, null, "Analyst", dept.getId(), managerId,
                new BigDecimal("90000"), LocalDate.of(2026, 3, 2));
    }

    @Test
    void onboardingCreatesLoginBalancesAndAuditEntry() {
        data.loginAs(hr);
        var result = employees.onboard(newHire("N-1", "Asha.Nair@Test.local", null), Role.EMPLOYEE);

        var account = accounts.findByEmployeeId(result.employee().getId()).orElseThrow();
        assertThat(account.getUsername()).isEqualTo("asha.nair@test.local");
        assertThat(account.isMustChangePassword()).isTrue();
        assertThat(encoder.matches(result.temporaryPassword(), account.getPasswordHash())).isTrue();
        assertThat(result.employee().isProfileCompleted()).isFalse();
        assertThat(leave.balances(result.employee(), 2026)).hasSize(LeaveType.values().length);
        assertThat(audit.search("Asha Nair", "Employee", 0).getContent())
                .anySatisfy(a -> {
                    assertThat(a.getAction()).isEqualTo("EMPLOYEE_ONBOARDED");
                    assertThat(a.getActor()).isEqualTo(data.account(hr).getUsername());
                });
    }

    @Test
    void duplicateEmailIsRejected() {
        data.loginAs(hr);
        employees.onboard(newHire("N-2", "dup@test.local", null), Role.EMPLOYEE);
        assertThatThrownBy(() -> employees.onboard(newHire("N-3", "DUP@test.local", null), Role.EMPLOYEE))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already in use");
    }

    @Test
    void onlySuperAdminCanCreateHrAdmins() {
        data.loginAs(hr);
        assertThatThrownBy(() -> employees.onboard(newHire("N-4", "hr2@test.local", null), Role.HR_ADMIN))
                .isInstanceOf(AccessDeniedException.class);
        data.asSuperAdmin();
        var result = employees.onboard(newHire("N-4", "hr2@test.local", null), Role.HR_ADMIN);
        assertThat(data.account(result.employee()).getRole()).isEqualTo(Role.HR_ADMIN);
    }

    @Test
    void salaryChangesAreAuditedWithOldAndNewValues() {
        Employee e = data.hire("Salaried", Role.EMPLOYEE, null, dept);
        data.loginAs(hr);
        var d = new EmployeeService.JobDetails(e.getEmployeeCode(), e.getFirstName(), e.getLastName(), e.getEmail(),
                e.getPhone(), "Senior Engineer", dept.getId(), null, new BigDecimal("125000"), e.getJoiningDate());
        employees.updateJobDetails(e.getId(), d);

        var entry = audit.search(e.getFullName(), "Employee", 0).getContent().stream()
                .filter(a -> a.getAction().equals("EMPLOYEE_UPDATED")).findFirst().orElseThrow();
        assertThat(entry.getDetails()).contains("salary: 100000 → 125000").contains("job title: Engineer → Senior Engineer");
    }

    @Test
    void reportingLoopsArePrevented() {
        Employee boss = data.hire("Boss", Role.MANAGER, null, dept);
        Employee report = data.hire("Report", Role.EMPLOYEE, boss, dept);
        data.loginAs(hr);
        var d = new EmployeeService.JobDetails(boss.getEmployeeCode(), boss.getFirstName(), boss.getLastName(),
                boss.getEmail(), null, boss.getJobTitle(), dept.getId(), report.getId(), boss.getSalary(), boss.getJoiningDate());
        assertThatThrownBy(() -> employees.updateJobDetails(boss.getId(), d))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("reporting loop");
    }

    @Test
    void profileMustIncludeEmergencyContact() {
        data.loginAs(hr);
        var result = employees.onboard(newHire("N-5", "p@test.local", null), Role.EMPLOYEE);
        assertThatThrownBy(() -> employees.updateProfile(result.employee(),
                new EmployeeService.Profile("+91-1", "Street", "", "")))
                .isInstanceOf(BusinessRuleException.class);
        employees.updateProfile(result.employee(), new EmployeeService.Profile("+91-1", "Street", "Mom", "+91-2"));
        assertThat(employees.get(result.employee().getId()).isProfileCompleted()).isTrue();
    }

    @Test
    void offboardingDisablesLoginAndCancelsLeaveNotYetTaken() {
        Employee manager = data.hire("Leaving", Role.MANAGER, null, dept);
        Employee report = data.hire("Stays", Role.EMPLOYEE, manager, dept);
        // manager's own leave: one approved before the exit date, one pending, one approved after exit
        var before = leave.apply(manager, new Application(LeaveType.ANNUAL, LocalDate.of(2026, 3, 5),
                LocalDate.of(2026, 3, 5), false, null));
        var pending = leave.apply(manager, new Application(LeaveType.CASUAL, LocalDate.of(2026, 3, 9),
                LocalDate.of(2026, 3, 9), false, null));
        var after = leave.apply(manager, new Application(LeaveType.ANNUAL, LocalDate.of(2026, 3, 23),
                LocalDate.of(2026, 3, 24), false, null));
        data.loginAs(hr);
        leave.hrDecision(before.getId(), true, null);
        leave.hrDecision(after.getId(), true, null);
        // a team request waiting for the leaving manager
        var teamRequest = leave.apply(report, new Application(LeaveType.ANNUAL, LocalDate.of(2026, 3, 16),
                LocalDate.of(2026, 3, 16), false, null));

        var result = employees.offboard(manager.getId(), LocalDate.of(2026, 3, 13), "Resignation");

        assertThat(result.employee().getStatus()).isEqualTo(EmployeeStatus.EXITED);
        assertThat(data.account(manager).isEnabled()).isFalse();
        assertThat(before.getStatus()).isEqualTo(LeaveStatus.APPROVED);     // already taken, kept
        assertThat(pending.getStatus()).isEqualTo(LeaveStatus.CANCELLED);
        assertThat(after.getStatus()).isEqualTo(LeaveStatus.CANCELLED);
        assertThat(result.leaveCancelled()).isEqualTo(2);
        assertThat(teamRequest.getStatus()).isEqualTo(LeaveStatus.PENDING_HR);
        assertThat(result.reportsWithoutManager()).isEqualTo(1);
        // the refunded days are back on the (now closed) balance
        assertThat(LeaveRulesTest.balance(leave.balances(manager, 2026), LeaveType.ANNUAL).used())
                .isEqualByComparingTo("1");
    }

    @Test
    void hrCannotOffboardThemselves() {
        data.loginAs(hr);
        assertThatThrownBy(() -> employees.offboard(hr.getId(), LocalDate.of(2026, 3, 4), "Resignation"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("yourself");
    }

    @Test
    void exitedEmployeesCannotApplyForLeave() {
        Employee e = data.hire("Gone", Role.EMPLOYEE, null, dept);
        data.loginAs(hr);
        employees.offboard(e.getId(), LocalDate.of(2026, 3, 4), "End of contract");
        assertThatThrownBy(() -> leave.apply(employees.get(e.getId()), new Application(LeaveType.ANNUAL,
                LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 9), false, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void passwordChangeEnforcesThePolicy() {
        Employee e = data.hire("Pw", Role.EMPLOYEE, null, dept);
        Long id = data.account(e).getId();
        assertThatThrownBy(() -> accountService.changePassword(id, "wrong", "Newpass123", "Newpass123"))
                .hasMessageContaining("current password");
        assertThatThrownBy(() -> accountService.changePassword(id, TestData.PASSWORD, "short1", "short1"))
                .hasMessageContaining("at least 8");
        assertThatThrownBy(() -> accountService.changePassword(id, TestData.PASSWORD, "lettersonly", "lettersonly"))
                .hasMessageContaining("letters and numbers");
        assertThatThrownBy(() -> accountService.changePassword(id, TestData.PASSWORD, "Newpass123", "Other123"))
                .hasMessageContaining("do not match");
        accountService.changePassword(id, TestData.PASSWORD, "Newpass123", "Newpass123");
        assertThat(encoder.matches("Newpass123", data.account(e).getPasswordHash())).isTrue();
    }

    @Test
    void temporaryPasswordsMeetThePolicy() {
        for (int i = 0; i < 50; i++) {
            assertThat(com.hrportal.service.PasswordPolicy.validate(
                    com.hrportal.service.PasswordPolicy.temporaryPassword())).isNull();
        }
    }
}
