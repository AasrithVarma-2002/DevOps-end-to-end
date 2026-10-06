package com.hrportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hrportal.domain.Department;
import com.hrportal.domain.DocumentStatus;
import com.hrportal.domain.DocumentType;
import com.hrportal.domain.Employee;
import com.hrportal.domain.Role;
import com.hrportal.service.AuditService;
import com.hrportal.service.BusinessRuleException;
import com.hrportal.service.EmployeeDocumentService;
import com.hrportal.service.EmployeeDocumentService.Upload;
import com.hrportal.service.EmployeeService;
import com.hrportal.service.NotificationService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** Employee documents: what can be uploaded, HR verification, letters, the checklist and who may open a file. */
@SpringBootTest
@Import(TestClockConfig.class)
@Transactional
class DocumentTest {

    static final byte[] PDF = "%PDF-1.7\n1 0 obj << >> endobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13};
    static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 16};

    @Autowired TestData data;
    @Autowired EmployeeDocumentService documents;
    @Autowired EmployeeService employees;
    @Autowired NotificationService notifications;
    @Autowired AuditService audit;

    Department dept;
    Employee hr;
    Employee hr2;
    Employee manager;
    Employee employee;

    @BeforeEach
    void setUp() {
        dept = data.department();
        hr = data.hire("Hr", Role.HR_ADMIN, null, dept);
        hr2 = data.hire("Hrtwo", Role.HR_ADMIN, null, dept);
        manager = data.hire("Manager", Role.MANAGER, null, dept);
        employee = data.hire("Employee", Role.EMPLOYEE, manager, dept);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Upload pdf(String name) {
        return new Upload(name, PDF);
    }

    private Long uploadAsEmployee(DocumentType type) {
        data.loginAs(employee);
        return documents.upload(employee, type, pdf("scan.pdf")).getId();
    }

    // ------------------------------------------------------------------ upload checks

    @Test
    void uploadIsStoredAsPendingAndHrIsNotified() {
        var d = documents.upload(employee, DocumentType.PAN_CARD, pdf("my pan.pdf"));
        assertThat(d.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(d.getContentType()).isEqualTo("application/pdf");
        assertThat(d.getSizeBytes()).isEqualTo(PDF.length);
        assertThat(d.getDocumentKey()).startsWith("documents/" + employee.getEmployeeCode().replaceAll("[^A-Za-z0-9_-]", "_") + "/")
                .endsWith(".pdf").doesNotContain("pan");
        assertThat(notifications.recent(data.account(hr).getId()))
                .anySatisfy(n -> assertThat(n.getMessage()).contains("uploaded their pan card"));
        assertThat(audit.search(null, "EmployeeDocument", 0).getContent())
                .anySatisfy(a -> assertThat(a.getAction()).isEqualTo("DOCUMENT_UPLOADED"));
    }

    @Test
    void theFileTypeIsDecidedByItsContentNotItsName() {
        var png = documents.upload(employee, DocumentType.ID_PROOF, new Upload("photo.pdf", PNG));
        assertThat(png.getContentType()).isEqualTo("image/png");
        assertThat(png.getFileName()).isEqualTo("photo.pdf.png");
        assertThat(documents.upload(employee, DocumentType.EDUCATION, new Upload("cert", JPEG)).getFileName())
                .isEqualTo("cert.jpg");
        assertThatThrownBy(() -> documents.upload(employee, DocumentType.ADDRESS_PROOF,
                new Upload("evil.pdf", "<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Only PDF, PNG and JPEG");
    }

    @Test
    void emptyAndOversizedFilesAreRefused() {
        assertThatThrownBy(() -> documents.upload(employee, DocumentType.PAN_CARD, new Upload("a.pdf", new byte[0])))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Choose a file");
        byte[] big = new byte[(int) EmployeeDocumentService.MAX_BYTES + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        assertThatThrownBy(() -> documents.upload(employee, DocumentType.PAN_CARD, new Upload("big.pdf", big)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("5 MB");
    }

    @Test
    void fileNamesAreCleanedForDisplay() {
        assertThat(documents.upload(employee, DocumentType.ID_PROOF, pdf("C:\\scans\\..\\my <ID>.PDF")).getFileName())
                .isEqualTo("my _ID_.PDF");
        assertThat(documents.upload(employee, DocumentType.PAN_CARD, pdf("../../")).getFileName()).isEqualTo("document.pdf");
    }

    @Test
    void employeesUploadOnlyTheirOwnKindsOfDocumentAndNotTwiceWhileWaiting() {
        assertThatThrownBy(() -> documents.upload(employee, DocumentType.OFFER_LETTER, pdf("offer.pdf")))
                .isInstanceOf(BusinessRuleException.class);
        documents.upload(employee, DocumentType.PAN_CARD, pdf("pan.pdf"));
        assertThatThrownBy(() -> documents.upload(employee, DocumentType.PAN_CARD, pdf("pan2.pdf")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already waiting");
    }

    @Test
    void employeesWhoLeftCannotUpload() {
        data.loginAs(hr);
        employees.offboard(employee.getId(), LocalDate.of(2026, 3, 4), "Resignation");
        assertThatThrownBy(() -> documents.upload(employee, DocumentType.PAN_CARD, pdf("pan.pdf")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("left");
    }

    // ------------------------------------------------------------------ HR review

    @Test
    void hrVerifiesAndTheEmployeeIsTold() {
        Long id = uploadAsEmployee(DocumentType.PAN_CARD);
        data.loginAs(hr);
        var d = documents.verify(id, " Looks good ");
        assertThat(d.getStatus()).isEqualTo(DocumentStatus.VERIFIED);
        assertThat(d.getReviewNote()).isEqualTo("Looks good");
        assertThat(d.getReviewedBy()).isEqualTo(data.account(hr).getUsername());
        assertThat(notifications.recent(data.account(employee).getId()))
                .anySatisfy(n -> assertThat(n.getMessage()).isEqualTo("HR verified your pan card"));
        assertThatThrownBy(() -> documents.reject(id, "too late"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already been reviewed");
    }

    @Test
    void rejectingNeedsAReasonAndAllowsANewUpload() {
        Long id = uploadAsEmployee(DocumentType.BANK_DETAILS);
        data.loginAs(hr);
        assertThatThrownBy(() -> documents.reject(id, "  ")).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("why");
        documents.reject(id, "The account number is cut off");
        assertThat(notifications.recent(data.account(employee).getId()))
                .anySatisfy(n -> assertThat(n.getMessage()).contains("The account number is cut off"));

        data.loginAs(employee);
        assertThat(documents.upload(employee, DocumentType.BANK_DETAILS, pdf("bank-again.pdf")).getStatus())
                .isEqualTo(DocumentStatus.PENDING);
    }

    @Test
    void hrCannotVerifyTheirOwnDocuments() {
        data.loginAs(hr);
        Long own = documents.upload(hr, DocumentType.PAN_CARD, pdf("pan.pdf")).getId();
        assertThatThrownBy(() -> documents.verify(own, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("own documents");
        data.loginAs(hr2);
        assertThat(documents.verify(own, null).getStatus()).isEqualTo(DocumentStatus.VERIFIED);
    }

    @Test
    void pendingListsWhatIsWaitingOldestFirst() {
        Long first = uploadAsEmployee(DocumentType.PAN_CARD);
        Long second = uploadAsEmployee(DocumentType.ID_PROOF);
        assertThat(documents.pending()).extracting(d -> d.getId()).containsSubsequence(first, second);
    }

    // ------------------------------------------------------------------ letters from HR

    @Test
    void hrIssuesLettersEvenAfterTheEmployeeHasLeft() {
        data.loginAs(hr);
        employees.offboard(employee.getId(), LocalDate.of(2026, 3, 4), "Resignation");
        var d = documents.issue(employee.getId(), DocumentType.EXPERIENCE_LETTER, pdf("experience.pdf"));
        assertThat(d.getStatus()).isEqualTo(DocumentStatus.ISSUED);
        assertThat(notifications.recent(data.account(employee).getId()))
                .anySatisfy(n -> assertThat(n.getMessage()).contains("Experience / relieving letter"));
        assertThatThrownBy(() -> documents.issue(employee.getId(), DocumentType.PAN_CARD, pdf("pan.pdf")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("letter");
    }

    // ------------------------------------------------------------------ checklist

    @Test
    void checklistCountsOnlyVerifiedRequiredDocuments() {
        Long pan = uploadAsEmployee(DocumentType.PAN_CARD);
        uploadAsEmployee(DocumentType.ID_PROOF);
        uploadAsEmployee(DocumentType.PREVIOUS_EMPLOYMENT);   // optional, not on the checklist
        data.loginAs(hr);
        documents.verify(pan, null);

        var checklist = documents.checklist(employee);
        assertThat(checklist.total()).isEqualTo(5);
        assertThat(checklist.done()).isEqualTo(1);
        assertThat(checklist.outstanding()).extracting(i -> i.type())
                .contains(DocumentType.ID_PROOF, DocumentType.ADDRESS_PROOF).doesNotContain(DocumentType.PAN_CARD);
        assertThat(documents.checklists()).anySatisfy(c -> {
            assertThat(c.employee().getId()).isEqualTo(employee.getId());
            assertThat(c.done()).isEqualTo(1);
        });
    }

    // ------------------------------------------------------------------ opening a file

    @Test
    void onlyTheOwnerAndHrCanOpenADocumentAndHrViewsAreAudited() {
        Long id = uploadAsEmployee(DocumentType.PAN_CARD);
        assertThat(documents.download(id).content()).isEqualTo(PDF);

        data.loginAs(manager);
        assertThatThrownBy(() -> documents.download(id)).isInstanceOf(AccessDeniedException.class);

        data.loginAs(hr);
        var download = documents.download(id);
        assertThat(download.content()).isEqualTo(PDF);
        assertThat(download.link()).isNull();   // local storage streams the file; S3 gives a link
        assertThat(audit.search(null, "EmployeeDocument", 0).getContent())
                .anySatisfy(a -> assertThat(a.getAction()).isEqualTo("DOCUMENT_VIEWED"));
    }
}
