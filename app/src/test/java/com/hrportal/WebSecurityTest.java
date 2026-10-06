package com.hrportal;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hrportal.domain.Department;
import com.hrportal.domain.Employee;
import com.hrportal.domain.Role;
import com.hrportal.service.EmployeeService;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** End-to-end HTTP behaviour: login journey, lockout, role-based access, pages and API. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class WebSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired TestData data;
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

    private String user(Employee e) {
        return data.account(e).getUsername();
    }

    // ------------------------------------------------------------------ public endpoints

    @Test
    void healthCheckIsPublicForKubernetesProbes() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    @Test
    void pagesRedirectToLoginWhenSignedOut() throws Exception {
        mvc.perform(get("/leave").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(containsString("Sign in")));
    }

    // ------------------------------------------------------------------ first login journey

    @Test
    void newHireMustChangePasswordThenCompleteProfile() throws Exception {
        data.asSuperAdmin();
        var hire = employees.onboard(new EmployeeService.JobDetails("NEW-" + System.nanoTime() % 100000, "Neha",
                "Kapoor", "neha" + System.nanoTime() + "@test.local", null, "Designer", dept.getId(), manager.getId(),
                new BigDecimal("80000"), LocalDate.of(2026, 3, 2)), Role.EMPLOYEE);
        SecurityContextHolder.clearContext();

        Cookie session = login(hire.username(), hire.temporaryPassword());
        mvc.perform(get("/").cookie(session)).andExpect(redirectedUrl("/account/password"));
        mvc.perform(get("/leave").cookie(session)).andExpect(redirectedUrl("/account/password"));

        mvc.perform(post("/account/password").cookie(session).with(csrf())
                        .param("currentPassword", hire.temporaryPassword())
                        .param("newPassword", "MyNewPass42").param("confirmPassword", "MyNewPass42"))
                .andExpect(redirectedUrl("/"));
        mvc.perform(get("/").cookie(session)).andExpect(redirectedUrl("/profile/edit"));

        mvc.perform(post("/profile/edit").cookie(session).with(csrf())
                        .param("phone", "+91-9876543210").param("address", "12 MG Road, Bengaluru")
                        .param("emergencyContactName", "Ravi Kapoor").param("emergencyContactPhone", "+91-9123456780"))
                .andExpect(redirectedUrl("/"));
        mvc.perform(get("/").cookie(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Hello, Neha")));
    }

    @Test
    void accountLocksAfterFiveWrongPasswords() throws Exception {
        String username = user(employee);
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/login").with(csrf()).param("username", username).param("password", "wrong"))
                    .andExpect(redirectedUrl(i < 4 ? "/login?error" : "/login?locked"));
        }
        // even the right password is refused now
        mvc.perform(post("/login").with(csrf()).param("username", username).param("password", TestData.PASSWORD))
                .andExpect(redirectedUrl("/login?locked"));
    }

    @Test
    void offboardedEmployeesCannotSignIn() throws Exception {
        data.loginAs(hr);
        employees.offboard(employee.getId(), LocalDate.of(2026, 3, 4), "Resignation");
        SecurityContextHolder.clearContext();
        mvc.perform(post("/login").with(csrf()).param("username", user(employee)).param("password", TestData.PASSWORD))
                .andExpect(redirectedUrl("/login?disabled"));
    }

    // ------------------------------------------------------------------ role-based access

    @Test
    void eachRoleSeesOnlyItsOwnAreas() throws Exception {
        expect(employee, 200, "/", "/leave", "/leave/apply", "/profile", "/notifications", "/attendance",
                "/attendance?month=2026-02", "/payslips");
        expect(employee, 403, "/team", "/team/approvals", "/team/attendance", "/hr/employees", "/hr/audit",
                "/hr/attendance", "/hr/payroll", "/admin/users");

        expect(manager, 200, "/team", "/team/approvals", "/team/attendance");
        expect(manager, 403, "/hr/employees", "/hr/approvals", "/hr/attendance", "/admin/users");

        expect(hr, 200, "/hr/employees", "/hr/employees/new", "/hr/employees/" + employee.getId(),
                "/hr/employees/" + employee.getId() + "/edit", "/hr/employees/" + employee.getId() + "/offboard",
                "/hr/departments", "/hr/holidays", "/hr/approvals", "/hr/leave", "/hr/audit", "/team",
                "/hr/attendance", "/hr/attendance?date=2026-03-02", "/hr/payroll");
        expect(hr, 403, "/admin/users");
    }

    @Test
    void superAdminCanManageUsers() throws Exception {
        mvc.perform(get("/admin/users").with(httpBasic("admin@hrportal.local", "Admin@12345")).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk()).andExpect(content().string(containsString(user(employee))));
    }

    @Test
    void managerApprovesFromTheTeamPage() throws Exception {
        mvc.perform(post("/leave").with(httpBasic(user(employee), TestData.PASSWORD)).with(csrf())
                        .param("type", "ANNUAL").param("startDate", "2026-03-09").param("endDate", "2026-03-10"))
                .andExpect(redirectedUrl("/leave"));
        mvc.perform(get("/team/approvals").with(httpBasic(user(manager), TestData.PASSWORD)))
                .andExpect(status().isOk()).andExpect(content().string(containsString(employee.getFullName())));
    }

    @Test
    void employeeChecksInAndOutFromTheAttendancePage() throws Exception {
        var auth = httpBasic(user(employee), TestData.PASSWORD);
        mvc.perform(post("/attendance/check-in").with(auth).with(csrf())).andExpect(redirectedUrl("/attendance"));
        mvc.perform(get("/attendance").with(auth)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Check out")));
        mvc.perform(post("/attendance/check-out").with(auth).with(csrf())).andExpect(redirectedUrl("/attendance"));
        mvc.perform(get("/attendance").with(auth)).andExpect(status().isOk())
                .andExpect(content().string(containsString("done for today")));
        mvc.perform(get("/team/attendance").with(httpBasic(user(manager), TestData.PASSWORD)))
                .andExpect(status().isOk()).andExpect(content().string(containsString(employee.getFullName())));
    }

    @Test
    void hrRunsAndFinalizesPayrollThenTheEmployeeDownloadsTheirPayslip() throws Exception {
        var hrAuth = httpBasic(user(hr), TestData.PASSWORD);
        var result = mvc.perform(post("/hr/payroll").with(hrAuth).with(csrf()).param("month", "2025-12"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String runPage = result.getResponse().getRedirectedUrl();
        mvc.perform(get(runPage).with(hrAuth)).andExpect(status().isOk())
                .andExpect(content().string(containsString(employee.getFullName())))
                .andExpect(content().string(containsString("Preview PDF")));
        mvc.perform(post(runPage + "/finalize").with(hrAuth).with(csrf())).andExpect(redirectedUrl(runPage));

        var auth = httpBasic(user(employee), TestData.PASSWORD);
        mvc.perform(get("/payslips").with(auth)).andExpect(status().isOk())
                .andExpect(content().string(containsString("December 2025")));
        String json = mvc.perform(get("/api/payslips").with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].month").value("2025-12"))
                .andExpect(jsonPath("$[0].documentKey").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String id = String.valueOf(com.jayway.jsonpath.JsonPath.<Integer>read(json, "$[0].id"));
        mvc.perform(get("/payslips/" + id + "/pdf").with(auth)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(get("/payslips/" + id + "/pdf").with(httpBasic(user(manager), TestData.PASSWORD)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ REST API

    @Test
    void apiChecksInAndShowsTheMonth() throws Exception {
        var auth = httpBasic(user(employee), TestData.PASSWORD);
        mvc.perform(post("/api/attendance/check-in").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workDate").value("2026-03-04"));
        mvc.perform(post("/api/attendance/check-in").with(auth)).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/attendance").with(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(31))
                .andExpect(jsonPath("$.days[3].status").value("WORKING"))
                .andExpect(jsonPath("$.days[0].status").value("WEEKEND"));
        mvc.perform(post("/api/hr/attendance/corrections").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isForbidden());
    }

    @Test
    void apiReturnsMyProfileAndBalances() throws Exception {
        mvc.perform(get("/api/me").with(httpBasic(user(employee), TestData.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.employee.managerName").value(manager.getFullName()))
                .andExpect(jsonPath("$.employee.salary").doesNotExist())
                .andExpect(jsonPath("$.balances[0].type").value("ANNUAL"));
    }

    @Test
    void apiRejectsInvalidLeaveWith422() throws Exception {
        mvc.perform(post("/api/leave").with(httpBasic(user(employee), TestData.PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ANNUAL\",\"startDate\":\"2026-03-10\",\"endDate\":\"2026-03-09\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("end date")));
    }

    @Test
    void apiEnforcesRoles() throws Exception {
        mvc.perform(get("/api/hr/employees").with(httpBasic(user(employee), TestData.PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/hr/employees").with(httpBasic(user(hr), TestData.PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeCode":"API-%d","firstName":"Api","lastName":"Hire","email":"api%d@test.local",
                                 "jobTitle":"QA","departmentId":%d,"salary":50000,"joiningDate":"2026-03-09","role":"EMPLOYEE"}
                                """.formatted(System.nanoTime() % 100000, System.nanoTime(), dept.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty());
        mvc.perform(get("/api/me").with(httpBasic(user(employee), "wrong"))).andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerDocsAreAvailableToSignedInUsers() throws Exception {
        mvc.perform(get("/v3/api-docs").with(httpBasic(user(employee), TestData.PASSWORD)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("/api/leave")));
    }

    // ------------------------------------------------------------------ helpers

    private void expect(Employee who, int status, String... paths) throws Exception {
        for (String path : paths) {
            mvc.perform(get(path).with(httpBasic(user(who), TestData.PASSWORD)).accept(MediaType.TEXT_HTML))
                    .andExpect(result -> {
                        if (result.getResponse().getStatus() != status) {
                            throw new AssertionError(who.getFirstName() + " " + path + ": expected " + status
                                    + " but got " + result.getResponse().getStatus());
                        }
                    });
        }
    }

    /** Signs in through the real login form and returns the session cookie (sessions live in the database). */
    private Cookie login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/login").with(csrf()).param("username", username).param("password", password))
                .andExpect(redirectedUrl("/")).andReturn();
        Cookie cookie = result.getResponse().getCookie("SESSION");
        if (cookie == null) {
            throw new AssertionError("No SESSION cookie after login");
        }
        return cookie;
    }
}
