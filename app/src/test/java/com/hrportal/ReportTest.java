package com.hrportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hrportal.domain.Department;
import com.hrportal.domain.Employee;
import com.hrportal.domain.Role;
import com.hrportal.service.AttendanceService;
import com.hrportal.service.DepartmentService;
import com.hrportal.service.NotFoundException;
import com.hrportal.service.PayrollService;
import com.hrportal.service.ReportService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** CSV reports: content, quoting and protection against spreadsheet formula injection. */
@SpringBootTest
@Import(TestClockConfig.class)
@Transactional
class ReportTest {

    @Autowired TestData data;
    @Autowired ReportService reports;
    @Autowired AttendanceService attendance;
    @Autowired PayrollService payroll;
    @Autowired DepartmentService departments;

    Employee hr;
    Employee employee;

    @BeforeEach
    void setUp() {
        Department dept = data.department();
        hr = data.hire("Hr", Role.HR_ADMIN, null, dept);
        employee = data.hire("Employee", Role.EMPLOYEE, hr, dept);
        data.loginAs(hr);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static String line(String csv, String containing) {
        return Arrays.stream(csv.split("\r\n")).filter(l -> l.contains(containing)).findFirst().orElse(null);
    }

    @Test
    void attendanceReportHasTheMonthsTotals() {
        attendance.correct(employee.getId(), LocalDate.of(2026, 3, 2), LocalTime.of(9, 0), LocalTime.of(18, 30), "Test");
        String csv = reports.attendance(YearMonth.of(2026, 3));
        assertThat(csv).startsWith("﻿Employee code,Name,Department,Present days,Half days,On leave,Absent days,Hours worked\r\n");
        assertThat(line(csv, employee.getEmployeeCode())).endsWith(",1,0,0,1,9.50"); // 2 Mar present, 3 Mar absent
    }

    @Test
    void leaveReportHasEveryLeaveType() {
        String csv = reports.leave(2026);
        assertThat(line(csv, employee.getEmployeeCode() + "," + employee.getFullName()))
                .contains(",Annual,20,0,0,20");
        assertThat(csv).contains(employee.getEmployeeCode() + "," + employee.getFullName() + ",")
                .contains(",Unpaid,,0,0,");   // unlimited type: no allowance
    }

    @Test
    void payrollRegisterListsEveryPayslip() {
        payroll.create(YearMonth.of(2026, 2));
        String csv = reports.payrollRegister(YearMonth.of(2026, 2));
        assertThat(line(csv, employee.getEmployeeCode()))
                .contains(",100000.00,28,28.0,0.0,50000.00,20000.00,30000.00,100000.00,1800.00,200.00,2000.00,98000.00,Draft");
        assertThatThrownBy(() -> reports.payrollRegister(YearMonth.of(2025, 1))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void cellsAreQuotedAndFormulasNeutralised() {
        Department tricky = departments.create("Sales, \"North\" " + System.nanoTime(), "Pune");
        Employee sneaky = data.hire("=HYPERLINK", Role.EMPLOYEE, hr, tricky);
        data.loginAs(hr);
        String row = line(reports.attendance(YearMonth.of(2026, 3)), sneaky.getEmployeeCode());
        assertThat(row).contains(",'=HYPERLINK Tester,");          // never starts with "=" in a cell
        assertThat(row).contains(",\"Sales, \"\"North\"\" ");       // comma and quotes escaped
    }
}
