package com.hrportal;

import static com.hrportal.LeaveRulesTest.balance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hrportal.domain.Department;
import com.hrportal.domain.Employee;
import com.hrportal.domain.LeaveStatus;
import com.hrportal.domain.LeaveType;
import com.hrportal.domain.Role;
import com.hrportal.repository.NotificationRepository;
import com.hrportal.service.BusinessRuleException;
import com.hrportal.service.LeaveService;
import com.hrportal.service.LeaveService.Application;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** Two-step approval: manager, then HR for leave over 5 days; deduction and refunds. */
@SpringBootTest
@Import(TestClockConfig.class)
@Transactional
class ApprovalFlowTest {

    @Autowired TestData data;
    @Autowired LeaveService leave;
    @Autowired NotificationRepository notifications;

    Employee manager;
    Employee employee;
    Employee hr;

    @BeforeEach
    void setUp() {
        Department dept = data.department();
        hr = data.hire("Hr", Role.HR_ADMIN, null, dept);
        manager = data.hire("Manager", Role.MANAGER, null, dept);
        employee = data.hire("Employee", Role.EMPLOYEE, manager, dept);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Application annual(String from, String to) {
        return new Application(LeaveType.ANNUAL, LocalDate.parse(from), LocalDate.parse(to), false, null);
    }

    private java.math.BigDecimal annualUsed() {
        return balance(leave.balances(employee, 2026), LeaveType.ANNUAL).used();
    }

    @Test
    void shortLeaveIsFinalAfterManagerApproval() {
        var request = leave.apply(employee, annual("2026-03-09", "2026-03-11"));   // 3 days
        assertThat(request.getStatus()).isEqualTo(LeaveStatus.PENDING_MANAGER);
        assertThat(request.getManager().getId()).isEqualTo(manager.getId());
        assertThat(notifications.countByRecipientIdAndReadFalse(data.account(manager).getId())).isPositive();

        leave.managerDecision(request.getId(), manager, true, "Enjoy");

        assertThat(request.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(annualUsed()).isEqualByComparingTo("3");
    }

    @Test
    void exactlyFiveDaysDoesNotNeedHr() {
        var request = leave.apply(employee, annual("2026-03-09", "2026-03-13"));   // Mon-Fri = 5
        leave.managerDecision(request.getId(), manager, true, null);
        assertThat(request.getStatus()).isEqualTo(LeaveStatus.APPROVED);
    }

    @Test
    void longLeaveNeedsHrAfterTheManager() {
        var request = leave.apply(employee, annual("2026-03-09", "2026-03-16"));   // 6 days
        leave.managerDecision(request.getId(), manager, true, "Fine by me");

        assertThat(request.getStatus()).isEqualTo(LeaveStatus.PENDING_HR);
        assertThat(annualUsed()).isEqualByComparingTo("0");                         // not deducted yet

        data.loginAs(hr);
        leave.hrDecision(request.getId(), true, "Approved");

        assertThat(request.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(request.getHrDecidedBy()).isEqualTo(data.account(hr).getUsername());
        assertThat(annualUsed()).isEqualByComparingTo("6");
    }

    @Test
    void rejectionDoesNotTouchTheBalance() {
        var request = leave.apply(employee, annual("2026-03-09", "2026-03-10"));
        leave.managerDecision(request.getId(), manager, false, "Release week");
        assertThat(request.getStatus()).isEqualTo(LeaveStatus.REJECTED);
        assertThat(request.getManagerComment()).isEqualTo("Release week");
        assertThat(annualUsed()).isEqualByComparingTo("0");
    }

    @Test
    void onlyTheAssignedManagerCanDecide() {
        Employee otherManager = data.hire("Other", Role.MANAGER, null, data.department());
        var request = leave.apply(employee, annual("2026-03-09", "2026-03-10"));
        assertThatThrownBy(() -> leave.managerDecision(request.getId(), otherManager, true, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aRequestCannotBeDecidedTwice() {
        var request = leave.apply(employee, annual("2026-03-09", "2026-03-10"));
        leave.managerDecision(request.getId(), manager, false, null);
        assertThatThrownBy(() -> leave.managerDecision(request.getId(), manager, true, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void employeesWithoutAManagerGoStraightToHr() {
        var request = leave.apply(manager, annual("2026-03-09", "2026-03-10"));
        assertThat(request.getStatus()).isEqualTo(LeaveStatus.PENDING_HR);
        assertThat(request.getManager()).isNull();
    }

    @Test
    void hrCannotApproveTheirOwnLeave() {
        var request = leave.apply(hr, annual("2026-03-09", "2026-03-10"));
        data.loginAs(hr);
        assertThatThrownBy(() -> leave.hrDecision(request.getId(), true, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("your own leave");
    }

    @Test
    void cancellingApprovedLeaveRefundsTheDays() {
        var request = leave.apply(employee, annual("2026-03-09", "2026-03-11"));
        leave.managerDecision(request.getId(), manager, true, null);
        assertThat(annualUsed()).isEqualByComparingTo("3");

        data.loginAs(employee);
        leave.cancel(request.getId());

        assertThat(request.getStatus()).isEqualTo(LeaveStatus.CANCELLED);
        assertThat(annualUsed()).isEqualByComparingTo("0");
    }

    @Test
    void employeeCannotCancelLeaveThatHasStartedButHrCan() {
        var request = leave.apply(employee, new Application(LeaveType.SICK, LocalDate.of(2026, 3, 3),
                LocalDate.of(2026, 3, 5), false, null));   // started yesterday
        leave.managerDecision(request.getId(), manager, true, null);

        data.loginAs(employee);
        assertThatThrownBy(() -> leave.cancel(request.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already started");

        data.loginAs(hr);
        leave.cancel(request.getId());
        assertThat(request.getStatus()).isEqualTo(LeaveStatus.CANCELLED);
    }

    @Test
    void employeesCannotCancelSomeoneElsesLeave() {
        Employee colleague = data.hire("Colleague", Role.EMPLOYEE, manager, data.department());
        var request = leave.apply(employee, annual("2026-03-09", "2026-03-10"));
        data.loginAs(colleague);
        assertThatThrownBy(() -> leave.cancel(request.getId())).isInstanceOf(AccessDeniedException.class);
    }
}
