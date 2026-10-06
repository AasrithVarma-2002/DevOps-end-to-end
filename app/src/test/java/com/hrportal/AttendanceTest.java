package com.hrportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hrportal.domain.DayStatus;
import com.hrportal.domain.Department;
import com.hrportal.domain.Employee;
import com.hrportal.domain.LeaveType;
import com.hrportal.domain.Role;
import com.hrportal.service.AttendanceService;
import com.hrportal.service.BusinessRuleException;
import com.hrportal.service.EmployeeService;
import com.hrportal.service.HolidayService;
import com.hrportal.service.LeaveService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** Attendance rules and the monthly calendar. "Today" is Wed 4 Mar 2026, 10:00 IST. */
@SpringBootTest
@Import(TestClockConfig.class)
@Transactional
class AttendanceTest {

    @Autowired TestData data;
    @Autowired AttendanceService attendance;
    @Autowired LeaveService leave;
    @Autowired HolidayService holidays;
    @Autowired EmployeeService employees;

    Department dept;
    Employee hr;
    Employee manager;
    Employee employee;

    @BeforeEach
    void setUp() {
        dept = data.department();
        hr = data.hire("Hr", Role.HR_ADMIN, null, dept);
        manager = data.hire("Manager", Role.MANAGER, null, dept);
        employee = data.hire("Employee", Role.EMPLOYEE, manager, dept);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private AttendanceService.Day day(Employee e, LocalDate date) {
        return attendance.month(e, YearMonth.from(date)).days().get(date.getDayOfMonth() - 1);
    }

    // ------------------------------------------------------------------ check in / out

    @Test
    void checkInOpensTodayAndCheckOutClosesIt() {
        var record = attendance.checkIn(employee);
        assertThat(record.getWorkDate()).isEqualTo(LocalDate.of(2026, 3, 4));
        assertThat(record.getCheckIn()).isEqualTo(LocalDate.of(2026, 3, 4).atTime(10, 0));
        assertThat(day(employee, LocalDate.of(2026, 3, 4)).status()).isEqualTo(DayStatus.WORKING);

        attendance.checkOut(employee);
        assertThat(record.isOpen()).isFalse();
        assertThat(record.getMinutesWorked()).isZero(); // the test clock doesn't move
        assertThat(day(employee, LocalDate.of(2026, 3, 4)).status()).isEqualTo(DayStatus.HALF_DAY);
    }

    @Test
    void onlyOneCheckInAndOneCheckOutPerDay() {
        assertThatThrownBy(() -> attendance.checkOut(employee))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("haven't checked in");
        attendance.checkIn(employee);
        assertThatThrownBy(() -> attendance.checkIn(employee))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already checked in");
        attendance.checkOut(employee);
        assertThatThrownBy(() -> attendance.checkOut(employee))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already checked out");
    }

    @Test
    void employeesWhoLeftCannotCheckIn() {
        data.loginAs(hr);
        employees.offboard(employee.getId(), LocalDate.of(2026, 3, 4), "Resignation");
        assertThatThrownBy(() -> attendance.checkIn(employee))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("left");
    }

    // ------------------------------------------------------------------ calendar

    @Test
    void calendarCombinesAttendanceLeaveHolidaysAndWeekends() {
        data.loginAs(hr);
        holidays.add(LocalDate.of(2026, 3, 5), "Test holiday");
        attendance.correct(employee.getId(), LocalDate.of(2026, 3, 2), LocalTime.of(9, 0), LocalTime.of(18, 30), "Badge reader down");

        data.loginAs(employee);
        var sick = leave.apply(employee, new LeaveService.Application(LeaveType.SICK, LocalDate.of(2026, 3, 3),
                LocalDate.of(2026, 3, 3), false, "Flu"));
        leave.managerDecision(sick.getId(), manager, true, null);

        var march = attendance.month(employee, YearMonth.of(2026, 3));
        assertThat(march.days()).hasSize(31);
        assertThat(march.days().get(0).status()).isEqualTo(DayStatus.WEEKEND);   // Sun 1
        assertThat(march.days().get(1).status()).isEqualTo(DayStatus.PRESENT);   // Mon 2: 9h 30m
        assertThat(march.days().get(1).detail()).contains("9h 30m");
        assertThat(march.days().get(2).status()).isEqualTo(DayStatus.ON_LEAVE);  // Tue 3
        assertThat(march.days().get(3).status()).isEqualTo(DayStatus.NOT_YET);   // Wed 4 = today, not in yet
        assertThat(march.days().get(4).status()).isEqualTo(DayStatus.HOLIDAY);   // Thu 5
        assertThat(march.days().get(5).status()).isEqualTo(DayStatus.NOT_YET);   // Fri 6 = future
        assertThat(march.summary().present()).isEqualTo(1);
        assertThat(march.summary().onLeave()).isEqualTo(1);
        assertThat(march.summary().minutesWorked()).isEqualTo(9 * 60 + 30);
    }

    @Test
    void pastWorkingDaysWithoutAttendanceAreAbsent() {
        var feb = attendance.month(employee, YearMonth.of(2026, 2));
        assertThat(feb.days().get(26).status()).isEqualTo(DayStatus.ABSENT);   // Fri 27 Feb
        assertThat(feb.days().get(27).status()).isEqualTo(DayStatus.WEEKEND);  // Sat 28 Feb
        assertThat(feb.summary().absent()).isEqualTo(20);                      // every weekday of Feb 2026
    }

    @Test
    void daysBeforeJoiningAreNotAbsent() {
        Employee newHire = data.hire("New", Role.EMPLOYEE, manager, dept, LocalDate.of(2026, 3, 3));
        assertThat(day(newHire, LocalDate.of(2026, 3, 2)).status()).isEqualTo(DayStatus.NOT_YET);
        assertThat(attendance.month(newHire, YearMonth.of(2026, 2)).summary().absent()).isZero();
    }

    @Test
    void shortDaysCountAsHalfDays() {
        data.loginAs(hr);
        attendance.correct(employee.getId(), LocalDate.of(2026, 3, 2), LocalTime.of(9, 0), LocalTime.of(12, 0), "Left early, doctor");
        assertThat(day(employee, LocalDate.of(2026, 3, 2)).status()).isEqualTo(DayStatus.HALF_DAY);
    }

    // ------------------------------------------------------------------ HR corrections

    @Test
    void correctionsAreOnlyForPastDaysWithValidTimesAndAReason() {
        data.loginAs(hr);
        LocalDate yesterday = LocalDate.of(2026, 3, 3);
        assertThatThrownBy(() -> attendance.correct(employee.getId(), LocalDate.of(2026, 3, 4),
                LocalTime.of(9, 0), LocalTime.of(18, 0), "x"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("past days");
        assertThatThrownBy(() -> attendance.correct(employee.getId(), yesterday,
                LocalTime.of(18, 0), LocalTime.of(9, 0), "x"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("after check-in");
        assertThatThrownBy(() -> attendance.correct(employee.getId(), yesterday,
                LocalTime.of(9, 0), LocalTime.of(18, 0), " "))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("reason");
        assertThatThrownBy(() -> attendance.correct(employee.getId(), LocalDate.of(2019, 6, 3),
                LocalTime.of(9, 0), LocalTime.of(18, 0), "x"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not employed");
    }

    @Test
    void correctingTheSameDayAgainUpdatesTheRecord() {
        data.loginAs(hr);
        LocalDate d = LocalDate.of(2026, 3, 2);
        var first = attendance.correct(employee.getId(), d, LocalTime.of(9, 0), LocalTime.of(17, 0), "Forgot");
        var second = attendance.correct(employee.getId(), d, LocalTime.of(8, 30), LocalTime.of(17, 30), "Fixed times");
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getMinutesWorked()).isEqualTo(9 * 60);
        assertThat(second.getCorrectedBy()).isEqualTo(data.account(hr).getUsername());
    }

    // ------------------------------------------------------------------ team and HR views

    @Test
    void managerSeesOnlyDirectReports() {
        Employee other = data.hire("Other", Role.EMPLOYEE, null, dept);
        attendance.checkIn(employee);
        var team = attendance.team(manager.getId());
        assertThat(team).extracting(t -> t.employee().getId()).containsExactly(employee.getId());
        assertThat(team.get(0).today().status()).isEqualTo(DayStatus.WORKING);
        assertThat(team).extracting(t -> t.employee().getId()).doesNotContain(other.getId());
    }

    @Test
    void hrSeesEveryoneForADay() {
        attendance.checkIn(employee);
        var rows = attendance.everyoneOn(LocalDate.of(2026, 3, 4));
        assertThat(rows).anySatisfy(r -> {
            assertThat(r.employee().getId()).isEqualTo(employee.getId());
            assertThat(r.today().status()).isEqualTo(DayStatus.WORKING);
        });
        assertThat(rows).anySatisfy(r -> {
            assertThat(r.employee().getId()).isEqualTo(manager.getId());
            assertThat(r.today().status()).isEqualTo(DayStatus.NOT_YET);
        });
    }
}
