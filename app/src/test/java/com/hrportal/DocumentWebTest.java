package com.hrportal;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hrportal.domain.Department;
import com.hrportal.domain.Employee;
import com.hrportal.domain.Role;
import com.hrportal.service.EmployeeDocumentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

/** Documents over HTTP: the upload form, HR's pages, opening files and the API. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class DocumentWebTest {

    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired EmployeeDocumentService documents;

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

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "pan.pdf", "application/pdf", DocumentTest.PDF);
    }

    private Long latestDocumentId(Employee e) {
        return documents.forEmployee(e.getId()).get(0).getId();
    }

    @Test
    void employeeUploadsHrVerifiesAndBothCanOpenTheFile() throws Exception {
        var auth = httpBasic(user(employee), TestData.PASSWORD);
        mvc.perform(get("/documents").with(auth)).andExpect(status().isOk())
                .andExpect(content().string(containsString("0 of 5 verified")));
        mvc.perform(multipart("/documents").file(pdf()).param("type", "PAN_CARD").with(auth).with(csrf()))
                .andExpect(redirectedUrl("/documents")).andExpect(flash().attributeExists("success"));
        Long id = latestDocumentId(employee);

        var hrAuth = httpBasic(user(hr), TestData.PASSWORD);
        mvc.perform(get("/hr/documents").with(hrAuth)).andExpect(status().isOk())
                .andExpect(content().string(containsString("pan.pdf")));
        mvc.perform(post("/hr/documents/" + id + "/verify").with(hrAuth).with(csrf()))
                .andExpect(redirectedUrl("/hr/documents"));
        mvc.perform(get("/hr/employees/" + employee.getId() + "/documents").with(hrAuth)).andExpect(status().isOk())
                .andExpect(content().string(containsString("1 of 5 verified")));

        for (var who : new Employee[]{employee, hr}) {
            mvc.perform(get("/documents/" + id + "/file").with(httpBasic(user(who), TestData.PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/pdf"))
                    .andExpect(header().string("Content-Disposition", containsString("pan.pdf")))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(content().bytes(DocumentTest.PDF));
        }
    }

    @Test
    void otherEmployeesAndManagersCannotOpenSomeoneElsesDocument() throws Exception {
        mvc.perform(multipart("/documents").file(pdf()).param("type", "PAN_CARD")
                .with(httpBasic(user(employee), TestData.PASSWORD)).with(csrf()));
        Long id = latestDocumentId(employee);
        mvc.perform(get("/documents/" + id + "/file").with(httpBasic(user(manager), TestData.PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/hr/documents").with(httpBasic(user(manager), TestData.PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aFileThatIsNotADocumentShowsAnError() throws Exception {
        var html = new MockMultipartFile("file", "pan.pdf", "application/pdf", "<html></html>".getBytes());
        mvc.perform(multipart("/documents").file(html).param("type", "PAN_CARD")
                        .with(httpBasic(user(employee), TestData.PASSWORD)).with(csrf()))
                .andExpect(redirectedUrl("/documents"))
                .andExpect(flash().attribute("error", containsString("Only PDF, PNG and JPEG")));
    }

    @Test
    void uploadsNeedTheCsrfToken() throws Exception {
        mvc.perform(multipart("/documents").file(pdf()).param("type", "PAN_CARD")
                .with(httpBasic(user(employee), TestData.PASSWORD))).andExpect(status().isForbidden());
    }

    @Test
    void hrSharesALetterThroughThePage() throws Exception {
        mvc.perform(multipart("/hr/employees/" + employee.getId() + "/documents").file(pdf()).param("type", "OFFER_LETTER")
                        .with(httpBasic(user(hr), TestData.PASSWORD)).with(csrf()))
                .andExpect(redirectedUrl("/hr/employees/" + employee.getId() + "/documents"));
        mvc.perform(get("/documents").with(httpBasic(user(employee), TestData.PASSWORD)))
                .andExpect(content().string(containsString("Offer letter")));
    }

    @Test
    void verifyRedirectsBackOnlyToHrPages() throws Exception {
        mvc.perform(multipart("/documents").file(pdf()).param("type", "PAN_CARD")
                .with(httpBasic(user(employee), TestData.PASSWORD)).with(csrf()));
        Long id = latestDocumentId(employee);
        mvc.perform(post("/hr/documents/" + id + "/verify").param("back", "https://evil.example")
                        .with(httpBasic(user(hr), TestData.PASSWORD)).with(csrf()))
                .andExpect(redirectedUrl("/hr/documents"));
    }

    @Test
    void apiUploadsListsAndRejects() throws Exception {
        var auth = httpBasic(user(employee), TestData.PASSWORD);
        mvc.perform(multipart("/api/documents").file(pdf()).param("type", "ID_PROOF").with(auth))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.documentKey").doesNotExist())
                .andExpect(jsonPath("$.employee").doesNotExist());
        Long id = latestDocumentId(employee);

        var hrAuth = httpBasic(user(hr), TestData.PASSWORD);
        mvc.perform(get("/api/hr/documents/pending").with(hrAuth)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").exists());
        mvc.perform(post("/api/hr/documents/" + id + "/reject").with(hrAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Blurry\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(get("/api/documents").with(auth))
                .andExpect(jsonPath("$[0].reviewNote").value("Blurry"));
        mvc.perform(get("/api/hr/documents/pending").with(auth)).andExpect(status().isForbidden());
    }
}
