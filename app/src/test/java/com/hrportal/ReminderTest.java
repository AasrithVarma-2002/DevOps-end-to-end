package com.hrportal;

import static org.assertj.core.api.Assertions.assertThat;

import com.hrportal.domain.Department;
import com.hrportal.domain.Employee;
import com.hrportal.domain.LeaveType;
import com.hrportal.domain.Notification;
import com.hrportal.domain.Role;
import com.hrportal.service.AttendanceService;
import com.hrportal.service.LeaveService;
import com.hrportal.service.NotificationService;
import com.hrportal.service.PayrollService;
import com.hrportal.service.ReminderService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** What the scheduled jobs send, and the ShedLock lock that stops two pods running a job twice. */
@SpringBootTest
@Import(TestClockConfig.class)
@Transactional
class ReminderTest {

    @Autowired TestData data;
    @Autowired ReminderService reminders;
    @Autowired LeaveService leave;
    @Autowired AttendanceService attendance;
    @Autowired PayrollService payroll;
    @Autowired NotificationService notifications;
    @Autowired DataSource dataSource;

    Employee hr;
    Employee manager;
    Employee employee;

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

    private List<String> inbox(Employee e) {
        return notifications.recent(data.account(e).getId()).stream().map(Notification::getMessage).toList();
    }

    @Test
    void managersAreRemindedOfRequestsWaitingForThem() {
        leave.apply(employee, new LeaveService.Application(LeaveType.CASUAL, LocalDate.of(2026, 3, 9),
                LocalDate.of(2026, 3, 9), false, null));
        reminders.remindPendingLeave();
        assertThat(inbox(manager)).contains("1 leave request is waiting for your approval");
    }

    @Test
    void hrIsRemindedOfRequestsWaitingForHr() {
        var longLeave = leave.apply(employee, new LeaveService.Application(LeaveType.ANNUAL, LocalDate.of(2026, 3, 9),
                LocalDate.of(2026, 3, 20), false, null));
        leave.managerDecision(longLeave.getId(), manager, true, null); // > 5 days: goes on to HR
        reminders.remindPendingLeave();
        assertThat(inbox(hr)).anySatisfy(m -> assertThat(m).endsWith("waiting for HR approval"));
    }

    @Test
    void onlyPeopleStillCheckedInAreRemindedToCheckOut() {
        Employee done = data.hire("Done", Role.EMPLOYEE, manager, dept());
        attendance.checkIn(employee);
        attendance.checkIn(done);
        attendance.checkOut(done);

        reminders.remindMissedCheckOuts();
        assertThat(inbox(employee)).contains("You checked in at 10:00 today but haven't checked out yet");
        assertThat(inbox(done)).noneSatisfy(m -> assertThat(m).contains("haven't checked out"));
    }

    @Test
    void hrIsRemindedUntilLastMonthsPayrollIsFinalized() {
        assertThat(reminders.remindPayrollDue()).isTrue();
        assertThat(inbox(hr)).contains("Payroll for February 2026 hasn't been run yet");

        data.loginAs(hr);
        var run = payroll.create(YearMonth.of(2026, 2));
        assertThat(reminders.remindPayrollDue()).isTrue();
        assertThat(inbox(hr)).contains("Payroll for February 2026 is still a draft. Review and finalize it.");

        payroll.finalizeRun(run.getId());
        assertThat(reminders.remindPayrollDue()).isFalse();
    }

    @Test
    void aJobLockHeldByOnePodCannotBeTakenByAnother() {
        var provider = new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource)).usingDbTime().build());
        String job = "testJob-" + System.nanoTime();
        var config = new LockConfiguration(Instant.now(), job, Duration.ofMinutes(5), Duration.ZERO);

        var podA = provider.lock(config);
        var podB = provider.lock(config);
        assertThat(podA).isPresent();
        assertThat(podB).isEmpty();           // the second pod skips the job

        podA.get().unlock();
        assertThat(provider.lock(config)).isPresent();
    }

    private Department dept() {
        return employee.getDepartment();
    }
}
