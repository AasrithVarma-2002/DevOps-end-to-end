package com.hrportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hrportal.domain.Department;
import com.hrportal.domain.Employee;
import com.hrportal.domain.EmployeeStatus;
import com.hrportal.domain.LeaveStatus;
import com.hrportal.domain.LeaveType;
import com.hrportal.domain.ResignationStatus;
import com.hrportal.domain.Role;
import com.hrportal.repository.UserAccountRepository;
import com.hrportal.service.BusinessRuleException;
import com.hrportal.service.EmployeeService;
import com.hrportal.service.LeaveService;
import com.hrportal.service.NotificationService;
import com.hrportal.service.ResignationService;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** Resignations: submit, withdraw, HR accept / decline, the notice period and the nightly exit. "Today" is Wed 4 Mar 2026. */
@SpringBootTest
@Import(TestClockConfig.class)
@Transactional
class ResignationTest {

    static final LocalDate TODAY = LocalDate.of(2026, 3, 4);

    @Autowired TestData data;
    @Autowired ResignationService resignations;
    @Autowired LeaveService leave;
    @Autowired EmployeeService employees;
    @Autowired NotificationService notifications;
    @Autowired UserAccountRepository accounts;

    Department dept;
    Employee hr;
    Employee hr2;
    Employee manager;
    Employee employee;

    @BeforeEach
    void setUp() {
        dept = data.department();
        hr = data.hire("Hr", Role.HR_ADMIN, null, dept);
        hr2 = data.hire("Hrtwo", Role.HR_ADMIN, null, dept);
        manager = data.hire("Manager", Role.MANAGER, null, dept);
        employee = data.hire("Employee", Role.EMPLOYEE, manager, dept);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Long resign(LocalDate lastDay) {
        data.loginAs(employee);
        return resignations.submit(employee, lastDay, "Moving to another city").getId();
    }

    // ------------------------------------------------------------------ employee

    @Test
    void resigningTellsTheManagerAndHrAndFlagsShortNotice() {
        var r = resignations.submit(employee, TODAY.plusDays(10), "Moving to another city");
        assertThat(r.getStatus()).isEqualTo(ResignationStatus.SUBMITTED);
        assertThat(resignations.isShortNotice(r)).isTrue();
        assertThat(notifications.recent(data.account(manager).getId()))
                .anySatisfy(n -> assertThat(n.getMessage()).contains("has resigned").contains("shorter than the 30-day"));
        assertThat(notifications.recent(data.account(hr).getId()))
                .anySatisfy(n -> assertThat(n.getMessage()).contains("has resigned"));
        assertThat(resignations.defaultLastDay()).isEqualTo(TODAY.plusDays(30));
    }

    @Test
    void aResignationNeedsAReasonAFutureDayAndOnlyOneAtATime() {
        assertThatThrownBy(() -> resignations.submit(employee, TODAY.plusDays(30), " "))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("reason");
        assertThatThrownBy(() -> resignations.submit(employee, TODAY.minusDays(1), "x"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("from today");
        resignations.submit(employee, TODAY.plusDays(30), "x");
        assertThatThrownBy(() -> resignations.submit(employee, TODAY.plusDays(40), "again"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already have");
    }

    @Test
    void theEmployeeCanWithdrawOnlyBeforeHrAccepts() {
        resign(TODAY.plusDays(30));
        assertThat(resignations.withdraw(employee).getStatus()).isEqualTo(ResignationStatus.WITHDRAWN);
        assertThat(resignations.current(employee.getId())).isEmpty();

        Long id = resign(TODAY.plusDays(30));   // can resign again after withdrawing
        data.loginAs(hr);
        resignations.accept(id, null, null);
        assertThatThrownBy(() -> resignations.withdraw(employee))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("talk to HR");
    }

    // ------------------------------------------------------------------ HR

    @Test
    void acceptingFixesTheLastDayAndTheEmployeeKeepsWorking() {
        Long id = resign(TODAY.plusDays(30));
        data.loginAs(hr);
        var r = resignations.accept(id, TODAY.plusDays(20), "Thank you for your work");
        assertThat(r.getStatus()).isEqualTo(ResignationStatus.ACCEPTED);
        assertThat(r.getLastWorkingDay()).isEqualTo(TODAY.plusDays(20));
        assertThat(employee.getLastWorkingDay()).isEqualTo(TODAY.plusDays(20));
        assertThat(employee.isActive()).isTrue();                       // serving notice
        assertThat(data.account(employee).isEnabled()).isTrue();
        assertThat(notifications.recent(data.account(employee).getId()))
                .anySatisfy(n -> assertThat(n.getMessage()).contains("accepted").contains(TODAY.plusDays(20).toString()));
        assertThat(resignations.servingNotice()).extracting(x -> x.getId()).contains(id);
    }

    @Test
    void leaveAfterTheLastDayIsCancelledAndCanNoLongerBeRequested() {
        data.loginAs(employee);
        var late = leave.apply(employee, new LeaveService.Application(LeaveType.ANNUAL, LocalDate.of(2026, 4, 20),
                LocalDate.of(2026, 4, 21), false, "Trip"));
        Long id = resign(TODAY.plusDays(30));
        data.loginAs(hr);
        resignations.accept(id, LocalDate.of(2026, 3, 31), null);
        assertThat(leave.get(late.getId()).getStatus()).isEqualTo(LeaveStatus.CANCELLED);

        data.loginAs(employee);
        assertThatThrownBy(() -> leave.apply(employee, new LeaveService.Application(LeaveType.ANNUAL,
                LocalDate.of(2026, 3, 30), LocalDate.of(2026, 4, 2), false, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("last working day");
    }

    @Test
    void decliningNeedsAReason() {
        Long id = resign(TODAY.plusDays(30));
        data.loginAs(hr);
        assertThatThrownBy(() -> resignations.decline(id, "")).isInstanceOf(BusinessRuleException.class);
        var r = resignations.decline(id, "Let's talk about a role change first");
        assertThat(r.getStatus()).isEqualTo(ResignationStatus.DECLINED);
        assertThat(employee.getLastWorkingDay()).isNull();
        assertThatThrownBy(() -> resignations.accept(id, null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already been handled");
    }

    @Test
    void hrCannotDecideTheirOwnResignationOrPickAPastDay() {
        data.loginAs(hr);
        Long own = resignations.submit(hr, TODAY.plusDays(30), "Leaving").getId();
        assertThatThrownBy(() -> resignations.accept(own, null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("own resignation");
        data.loginAs(hr2);
        assertThatThrownBy(() -> resignations.accept(own, TODAY.minusDays(1), null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("past");
        assertThat(resignations.accept(own, null, null).getLastWorkingDay()).isEqualTo(TODAY.plusDays(30));
    }

    // ------------------------------------------------------------------ nightly exit

    @Test
    void theNightAfterTheLastDayTheEmployeeIsOffboarded() {
        Long id = resign(TODAY);
        data.loginAs(hr);
        resignations.accept(id, TODAY, null);
        SecurityContextHolder.clearContext();   // the job runs as nobody

        assertThat(resignations.completeDue()).isZero();   // still today: not yet

        var r = resignations.get(id);
        // pretend the clock moved on: an acceptance dated yesterday is due now
        r.decide(ResignationStatus.ACCEPTED, TODAY.minusDays(1), "hr", r.getDecidedAt(), null);
        employee.setLastWorkingDay(TODAY.minusDays(1));
        assertThat(resignations.completeDue()).isEqualTo(1);
        assertThat(resignations.get(id).getStatus()).isEqualTo(ResignationStatus.COMPLETED);
        assertThat(employee.getStatus()).isEqualTo(EmployeeStatus.EXITED);
        assertThat(employee.getExitReason()).isEqualTo("Resignation");
        assertThat(accounts.findByEmployeeId(employee.getId()).orElseThrow().isEnabled()).isFalse();
        assertThat(notifications.recent(data.account(hr).getId()))
                .anySatisfy(n -> assertThat(n.getMessage()).contains("has left"));
    }

    @Test
    void anOpenResignationClosesIfHrOffboardsDirectly() {
        Long id = resign(TODAY.plusDays(30));
        data.loginAs(hr);
        employees.offboard(employee.getId(), TODAY, "Resignation");
        resignations.completeDue();
        assertThat(resignations.get(id).getStatus()).isEqualTo(ResignationStatus.COMPLETED);
    }
}
