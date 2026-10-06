package com.hrportal;

import static org.assertj.core.api.Assertions.assertThat;

import com.hrportal.domain.Department;
import com.hrportal.domain.Role;
import com.hrportal.email.LoggingEmailService;
import com.hrportal.service.EmailService;
import com.hrportal.service.EmployeeService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

/** Notification emails go out only after the change commits; failures never undo the change. */
@SpringBootTest
@Import(TestClockConfig.class)
class EmailTest {

    @Autowired TestData data;
    @Autowired EmployeeService employees;
    @Autowired EmailService email;
    @Autowired TransactionTemplate tx;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private LoggingEmailService outbox() {
        return (LoggingEmailService) email;
    }

    private EmployeeService.JobDetails hire(Department dept, String address) {
        return new EmployeeService.JobDetails("M-" + System.nanoTime() % 1000000, "Meera", "Nair", address, null,
                "Designer", dept.getId(), null, new BigDecimal("50000"), LocalDate.of(2026, 3, 2));
    }

    @Test
    void committedChangesEmailTheRecipient() {
        Department dept = data.department();
        String address = "meera" + System.nanoTime() + "@example.com";
        data.asSuperAdmin();
        tx.executeWithoutResult(s -> employees.onboard(hire(dept, address), Role.EMPLOYEE));

        assertThat(outbox().sent()).anySatisfy(m -> {
            assertThat(m.to()).isEqualTo(address);
            assertThat(m.subject()).startsWith("HR Portal Pro: Welcome to HR Portal Pro, Meera");
            assertThat(m.body()).startsWith("Hello Meera,").contains("Sign in to HR Portal Pro");
        });
    }

    @Test
    void rolledBackChangesSendNothing() {
        Department dept = data.department();
        String address = "rollback" + System.nanoTime() + "@example.com";
        data.asSuperAdmin();
        tx.executeWithoutResult(s -> {
            employees.onboard(hire(dept, address), Role.EMPLOYEE);
            s.setRollbackOnly();
        });
        assertThat(outbox().sent()).noneSatisfy(m -> assertThat(m.to()).isEqualTo(address));
    }

    @Test
    void reservedDotLocalAddressesAreNeverEmailed() {
        Department dept = data.department();
        String address = "demo" + System.nanoTime() + "@hrportal.local";
        data.asSuperAdmin();
        tx.executeWithoutResult(s -> employees.onboard(hire(dept, address), Role.EMPLOYEE));
        assertThat(outbox().sent()).noneSatisfy(m -> assertThat(m.to()).isEqualTo(address));
    }
}
