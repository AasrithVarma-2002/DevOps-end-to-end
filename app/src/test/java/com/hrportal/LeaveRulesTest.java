package com.hrportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hrportal.domain.Department;
import com.hrportal.domain.Employee;
import com.hrportal.domain.LeaveType;
import com.hrportal.domain.Role;
import com.hrportal.service.BusinessRuleException;
import com.hrportal.service.HolidayService;
import com.hrportal.service.LeaveService;
import com.hrportal.service.LeaveService.Application;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** Leave rules: counting, balances, overlaps and date limits. "Today" is Wed 4 Mar 2026. */
@SpringBootTest
@Import(TestClockConfig.class)
@Transactional
class LeaveRulesTest {

    @Autowired TestData data;
    @Autowired LeaveService leave;
    @Autowired HolidayService holidays;

    Employee employee;

    @BeforeEach
    void setUp() {
        Department dept = data.department();
        Employee manager = data.hire("Manager", Role.MANAGER, null, dept);
        employee = data.hire("Employee", Role.EMPLOYEE, manager, dept);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Application annual(String from, String to) {
        return new Application(LeaveType.ANNUAL, LocalDate.parse(from), LocalDate.parse(to), false, null);
    }

    @Test
    void countsOnlyWorkingDaysSkippingWeekendsAndHolidays() {
        data.asSuperAdmin();
        holidays.add(LocalDate.of(2026, 3, 6), "Test holiday");
        // Thu 5 → Tue 10 March: Thu, (Fri holiday), (Sat, Sun), Mon, Tue = 3 days
        var request = leave.apply(employee, annual("2026-03-05", "2026-03-10"));
        assertThat(request.getDays()).isEqualByComparingTo("3");
    }

    @Test
    void halfDayCountsAsHalf() {
        var request = leave.apply(employee, new Application(LeaveType.CASUAL, LocalDate.of(2026, 3, 5),
                LocalDate.of(2026, 3, 5), true, null));
        assertThat(request.getDays()).isEqualByComparingTo("0.5");
    }

    @Test
    void halfDayMustBeASingleDate() {
        assertThatThrownBy(() -> leave.apply(employee, new Application(LeaveType.CASUAL, LocalDate.of(2026, 3, 5),
                LocalDate.of(2026, 3, 6), true, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("half day");
    }

    @Test
    void weekendOnlyRequestIsRejected() {
        assertThatThrownBy(() -> leave.apply(employee, annual("2026-03-07", "2026-03-08")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("no leave is needed");
    }

    @Test
    void cannotRequestMoreThanTheBalance() {
        // 5 Mar → 2 Apr is 21 working days; annual allowance is 20
        assertThatThrownBy(() -> leave.apply(employee, annual("2026-03-05", "2026-04-02")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Not enough annual leave");
    }

    @Test
    void pendingRequestsReserveTheirDays() {
        leave.apply(employee, annual("2026-03-09", "2026-03-20"));   // 10 days pending
        leave.apply(employee, annual("2026-04-06", "2026-04-17"));   // 10 more: exactly 20
        assertThatThrownBy(() -> leave.apply(employee, annual("2026-05-04", "2026-05-04")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("have 0 available");
    }

    @Test
    void unpaidLeaveHasNoLimit() {
        var request = leave.apply(employee, new Application(LeaveType.UNPAID, LocalDate.of(2026, 3, 5),
                LocalDate.of(2026, 5, 29), false, "Sabbatical"));
        assertThat(request.getDays()).isGreaterThan(new BigDecimal("20"));
    }

    @Test
    void overlappingRequestsAreBlocked() {
        leave.apply(employee, annual("2026-03-09", "2026-03-11"));
        assertThatThrownBy(() -> leave.apply(employee, new Application(LeaveType.SICK, LocalDate.of(2026, 3, 11),
                LocalDate.of(2026, 3, 12), false, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("overlap");
    }

    @Test
    void onlySickLeaveCanBeBackdatedAndOnlyUpToSevenDays() {
        assertThatThrownBy(() -> leave.apply(employee, annual("2026-03-02", "2026-03-02")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Only sick leave");

        var sick = leave.apply(employee, new Application(LeaveType.SICK, LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 3), false, "Flu"));
        assertThat(sick.getDays()).isEqualByComparingTo("2");

        assertThatThrownBy(() -> leave.apply(employee, new Application(LeaveType.SICK, LocalDate.of(2026, 2, 20),
                LocalDate.of(2026, 2, 20), false, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("7 days");
    }

    @Test
    void endDateBeforeStartDateIsRejected() {
        assertThatThrownBy(() -> leave.apply(employee, annual("2026-03-10", "2026-03-09")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("end date");
    }

    @Test
    void leaveCannotCrossTheYearEnd() {
        assertThatThrownBy(() -> leave.apply(employee, annual("2026-12-30", "2027-01-04")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("year end");
    }

    @Test
    void annualLeaveIsProRatedForMidYearJoiners() {
        Employee joiner = data.hire("Joiner", Role.EMPLOYEE, null, data.department(), LocalDate.of(2026, 3, 2));
        var balances = leave.balances(joiner, 2026);
        // joined in March: 10 of 12 months → 20 × 10/12 = 16.67, rounded down to 16.5
        assertThat(balance(balances, LeaveType.ANNUAL).allocated()).isEqualByComparingTo("16.5");
        assertThat(balance(balances, LeaveType.SICK).allocated()).isEqualByComparingTo("10");
        assertThat(balance(balances, LeaveType.CASUAL).allocated()).isEqualByComparingTo("6");
        assertThat(balance(balances, LeaveType.MATERNITY).allocated()).isEqualByComparingTo("180");
        assertThat(balance(balances, LeaveType.PATERNITY).allocated()).isEqualByComparingTo("10");
        assertThat(balance(balances, LeaveType.UNPAID).allocated()).isNull();
    }

    @Test
    void cannotApplyBeforeJoiningDate() {
        Employee joiner = data.hire("Future", Role.EMPLOYEE, null, data.department(), LocalDate.of(2026, 4, 1));
        assertThatThrownBy(() -> leave.apply(joiner, annual("2026-03-16", "2026-03-16")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("joining date");
    }

    static LeaveService.BalanceView balance(java.util.List<LeaveService.BalanceView> list, LeaveType type) {
        return list.stream().filter(b -> b.type() == type).findFirst().orElseThrow();
    }
}
