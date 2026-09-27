package com.hrportal.bootstrap;

import com.hrportal.domain.Employee;
import com.hrportal.domain.LeaveType;
import com.hrportal.domain.Role;
import com.hrportal.repository.EmployeeRepository;
import com.hrportal.repository.UserAccountRepository;
import com.hrportal.service.DepartmentService;
import com.hrportal.service.EmployeeService;
import com.hrportal.service.HolidayService;
import com.hrportal.service.LeaveService;
import com.hrportal.service.UserAccountService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs once at startup:
 * 1. creates the first Super Admin when the database has no logins (every environment), and
 * 2. optionally loads demo data (local runs and demos; off by default in AWS).
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    static final String DEMO_PASSWORD = "Password@123";

    private final UserAccountService accounts;
    private final UserAccountRepository accountRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentService departments;
    private final HolidayService holidays;
    private final EmployeeService employees;
    private final LeaveService leave;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String adminEmail;
    private final String adminPassword;
    private final boolean demoData;

    public DataInitializer(UserAccountService accounts, UserAccountRepository accountRepository,
                           EmployeeRepository employeeRepository, DepartmentService departments,
                           HolidayService holidays, EmployeeService employees, LeaveService leave,
                           PasswordEncoder encoder, TransactionTemplate tx, Clock clock,
                           @Value("${app.bootstrap.admin-email}") String adminEmail,
                           @Value("${app.bootstrap.admin-password}") String adminPassword,
                           @Value("${app.demo-data:false}") boolean demoData) {
        this.accounts = accounts;
        this.accountRepository = accountRepository;
        this.employeeRepository = employeeRepository;
        this.departments = departments;
        this.holidays = holidays;
        this.employees = employees;
        this.leave = leave;
        this.encoder = encoder;
        this.tx = tx;
        this.clock = clock;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.demoData = demoData;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Boolean created = tx.execute(s -> accounts.bootstrapSuperAdmin(adminEmail, adminPassword));
            if (Boolean.TRUE.equals(created)) {
                log.info("Created Super Admin {}", adminEmail);
            }
            if (demoData && employeeRepository.count() == 0) {
                tx.executeWithoutResult(s -> loadDemoData());
                log.info("Loaded demo data (all demo users use password {})", DEMO_PASSWORD);
            }
        } catch (DataIntegrityViolationException e) {
            // Another replica started at the same moment and won the race; its data is used.
            log.warn("Startup data was created by another instance: {}", e.getMostSpecificCause().getMessage());
        }
    }

    private void loadDemoData() {
        LocalDate today = LocalDate.now(clock);
        int year = today.getYear();

        var engineering = departments.create("Engineering", "Hyderabad");
        var hr = departments.create("Human Resources", "Bengaluru");
        var finance = departments.create("Finance", "Mumbai");
        departments.create("Sales", "Pune");

        for (int y = year; y <= year + 1; y++) {
            holidays.add(LocalDate.of(y, 1, 26), "Republic Day");
            holidays.add(LocalDate.of(y, 8, 15), "Independence Day");
            holidays.add(LocalDate.of(y, 10, 2), "Gandhi Jayanti");
            holidays.add(LocalDate.of(y, 12, 25), "Christmas");
        }

        LocalDate longAgo = LocalDate.of(year - 3, 4, 1);
        hire("EMP-1001", "Ananya", "Iyer", "ananya.iyer@hrportal.local", "HR Manager",
                hr.getId(), null, "120000", longAgo, Role.HR_ADMIN);
        Employee priya = hire("EMP-1002", "Priya", "Sharma", "priya.sharma@hrportal.local", "Engineering Manager",
                engineering.getId(), null, "210000", longAgo, Role.MANAGER);
        Employee vikram = hire("EMP-1003", "Vikram", "Rao", "vikram.rao@hrportal.local", "Finance Manager",
                finance.getId(), null, "180000", longAgo, Role.MANAGER);
        hire("EMP-1004", "Rahul", "Verma", "rahul.verma@hrportal.local", "DevOps Engineer",
                engineering.getId(), priya.getId(), "150000", LocalDate.of(year - 1, 1, 3), Role.EMPLOYEE);
        Employee karthik = hire("EMP-1005", "Karthik", "Reddy", "karthik.reddy@hrportal.local", "Software Engineer",
                engineering.getId(), priya.getId(), "130000", LocalDate.of(year - 1, 6, 1), Role.EMPLOYEE);
        Employee sneha = hire("EMP-1006", "Sneha", "Patel", "sneha.patel@hrportal.local", "Financial Analyst",
                finance.getId(), vikram.getId(), "110000", LocalDate.of(year - 2, 9, 15), Role.EMPLOYEE);

        // A request waiting for Priya (manager) and one approved leave that is happening now
        LocalDate nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        leave.apply(karthik, new LeaveService.Application(LeaveType.ANNUAL, nextMonday, nextMonday.plusDays(1),
                false, "Family function"));
        if (holidays.isWorkingDay(today)) {
            var snehaLeave = leave.apply(sneha, new LeaveService.Application(LeaveType.SICK, today, today, false, "Fever"));
            leave.managerDecision(snehaLeave.getId(), vikram, true, "Get well soon");
        }
    }

    private Employee hire(String code, String first, String last, String email, String title, Long deptId,
                          Long managerId, String salary, LocalDate joined, Role role) {
        var result = employees.onboard(new EmployeeService.JobDetails(code, first, last, email, "+91-90000"
                + code.substring(4), title, deptId, managerId, new BigDecimal(salary), joined), Role.EMPLOYEE);
        Employee e = result.employee();
        e.setAddress("Demo address, " + e.getDepartment().getLocation());
        e.setEmergencyContactName("Family contact");
        e.setEmergencyContactPhone("+91-99999" + code.substring(4));
        e.setProfileCompleted(true);
        // demo logins are ready to use: known password, no forced change, requested role
        var account = accountRepository.findByEmployeeId(e.getId()).orElseThrow();
        account.setPasswordHash(encoder.encode(DEMO_PASSWORD));
        account.setMustChangePassword(false);
        account.setRole(role);
        return e;
    }
}
