package com.hrportal;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hrportal.domain.Department;
import com.hrportal.domain.Employee;
import com.hrportal.domain.Role;
import com.hrportal.service.ResignationService;
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

/** Resignation over HTTP: the employee page, HR's page and the API. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class ResignationWebTest {

    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired ResignationService resignations;

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

    private String user(Employee e) {
        return data.account(e).getUsername();
    }

    @Test
    void employeeResignsAndHrAcceptsThroughThePages() throws Exception {
        var auth = httpBasic(user(employee), TestData.PASSWORD);
        mvc.perform(get("/resignation").with(auth)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Submit resignation")));
        mvc.perform(post("/resignation").with(auth).with(csrf())
                        .param("lastDay", "2026-04-03").param("reason", "Moving to Pune"))
                .andExpect(redirectedUrl("/resignation"));
        mvc.perform(get("/resignation").with(auth))
                .andExpect(content().string(containsString("Waiting for HR")))
                .andExpect(content().string(containsString("Withdraw resignation")));

        var hrAuth = httpBasic(user(hr), TestData.PASSWORD);
        mvc.perform(get("/hr/resignations").with(hrAuth)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Moving to Pune")));
        Long id = resignations.current(employee.getId()).orElseThrow().getId();
        mvc.perform(post("/hr/resignations/" + id + "/accept").with(hrAuth).with(csrf())
                        .param("lastWorkingDay", "2026-03-31"))
                .andExpect(redirectedUrl("/hr/resignations"));

        mvc.perform(get("/resignation").with(auth))
                .andExpect(content().string(containsString("Tue 31 Mar 2026")))
                .andExpect(content().string(containsString("serving notice")));
        mvc.perform(get("/hr/employees/" + employee.getId()).with(hrAuth))
                .andExpect(content().string(containsString("Serving notice")));
    }

    @Test
    void managersCannotOpenTheHrPage() throws Exception {
        mvc.perform(get("/hr/resignations").with(httpBasic(user(manager), TestData.PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void apiResignsAndHrDeclines() throws Exception {
        var auth = httpBasic(user(employee), TestData.PASSWORD);
        mvc.perform(post("/api/resignation").with(auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastDay\":\"2026-04-03\",\"reason\":\"Higher studies\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.employee").doesNotExist());
        Long id = resignations.current(employee.getId()).orElseThrow().getId();
        mvc.perform(post("/api/hr/resignations/" + id + "/decline").with(httpBasic(user(hr), TestData.PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Please stay\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DECLINED"));
        mvc.perform(post("/api/hr/resignations/" + id + "/accept").with(auth))
                .andExpect(status().isForbidden());
    }
}
