package com.hrportal;

import com.hrportal.domain.Department;
import com.hrportal.domain.Employee;
import com.hrportal.domain.Role;
import com.hrportal.domain.UserAccount;
import com.hrportal.repository.UserAccountRepository;
import com.hrportal.security.HrUserPrincipal;
import com.hrportal.service.DepartmentService;
import com.hrportal.service.EmployeeService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Builds employees for tests. Every call uses unique codes and emails. */
@Component
public class TestData {

    public static final String PASSWORD = "Secret123";
    private static final AtomicInteger SEQ = new AtomicInteger();

    private final DepartmentService departments;
    private final EmployeeService employees;
    private final UserAccountRepository accounts;
    private final PasswordEncoder encoder;

    public TestData(DepartmentService departments, EmployeeService employees, UserAccountRepository accounts,
                    PasswordEncoder encoder) {
        this.departments = departments;
        this.employees = employees;
        this.accounts = accounts;
        this.encoder = encoder;
    }

    public Department department() {
        return departments.create("Dept " + SEQ.incrementAndGet() + "-" + System.nanoTime(), "Hyderabad");
    }

    /** Hires someone who joined years ago, with a known password and a completed profile. */
    public Employee hire(String firstName, Role role, Employee manager, Department dept) {
        return hire(firstName, role, manager, dept, LocalDate.of(2020, 1, 6));
    }

    public Employee hire(String firstName, Role role, Employee manager, Department dept, LocalDate joined) {
        int n = SEQ.incrementAndGet();
        asSuperAdmin();
        var result = employees.onboard(new EmployeeService.JobDetails("T-" + n + "-" + System.nanoTime() % 100000,
                firstName, "Tester", firstName.toLowerCase() + n + "." + System.nanoTime() + "@test.local", null,
                "Engineer", dept.getId(), manager == null ? null : manager.getId(), new BigDecimal("100000"), joined), role);
        Employee e = result.employee();
        UserAccount account = accounts.findByEmployeeId(e.getId()).orElseThrow();
        account.setPasswordHash(encoder.encode(PASSWORD));
        account.setMustChangePassword(false);
        accounts.save(account);
        employees.updateProfile(e, new EmployeeService.Profile("+91-9000000000", "1 Test Street", "Kin", "+91-9111111111"));
        SecurityContextHolder.clearContext();
        return e;
    }

    public UserAccount account(Employee e) {
        return accounts.findByEmployeeId(e.getId()).orElseThrow();
    }

    /** Signs in (for service calls) as the given employee. */
    public void loginAs(Employee e) {
        login(account(e));
    }

    public void asSuperAdmin() {
        login(accounts.findByUsernameIgnoreCase("admin@hrportal.local").orElseThrow());
    }

    private void login(UserAccount account) {
        var principal = new HrUserPrincipal(account, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
