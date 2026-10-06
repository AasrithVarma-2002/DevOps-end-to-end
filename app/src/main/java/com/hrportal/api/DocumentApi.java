package com.hrportal.api;

import com.hrportal.domain.DocumentType;
import com.hrportal.domain.EmployeeDocument;
import com.hrportal.security.CurrentUser;
import com.hrportal.service.EmployeeDocumentService;
import com.hrportal.storage.Dispositions;
import com.hrportal.web.DocumentController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@Tag(name = "Documents", description = "My documents (everyone), verification and letters (HR)")
public class DocumentApi {

    private final CurrentUser currentUser;
    private final EmployeeDocumentService documents;

    public DocumentApi(CurrentUser currentUser, EmployeeDocumentService documents) {
        this.currentUser = currentUser;
        this.documents = documents;
    }

    public record Review(String note) {
    }

    @GetMapping("/documents")
    @Operation(summary = "My documents, newest first")
    public List<EmployeeDocument> mine() {
        return documents.forEmployee(currentUser.requireEmployee().getId());
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a document for HR to verify (PDF, PNG or JPEG, up to 5 MB)")
    public EmployeeDocument upload(@RequestParam DocumentType type, @RequestParam MultipartFile file) {
        return documents.upload(currentUser.requireEmployee(), type, DocumentController.upload(file));
    }

    @GetMapping("/documents/{id}/file")
    @Operation(summary = "The file (own documents; HR: any). In AWS a redirect to a 5-minute S3 link.")
    public ResponseEntity<byte[]> file(@PathVariable Long id) {
        var download = documents.download(id);
        if (download.link() != null) {
            return ResponseEntity.status(HttpStatus.FOUND).location(download.link()).build();
        }
        var d = download.document();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        Dispositions.attachment(d.getFileName()))
                .body(download.content());
    }

    @GetMapping("/hr/documents/pending")
    @Operation(summary = "Documents waiting for verification, oldest first (HR)")
    public List<EmployeeDocument> pending() {
        return documents.pending();
    }

    @PostMapping("/hr/documents/{id}/verify")
    @Operation(summary = "Mark a document verified, with an optional note (HR)")
    public EmployeeDocument verify(@PathVariable Long id, @RequestBody(required = false) Review body) {
        return documents.verify(id, body == null ? null : body.note());
    }

    @PostMapping("/hr/documents/{id}/reject")
    @Operation(summary = "Reject a document; the note is the reason shown to the employee (HR)")
    public EmployeeDocument reject(@PathVariable Long id, @RequestBody Review body) {
        return documents.reject(id, body.note());
    }

    @GetMapping("/hr/employees/{id}/documents")
    @Operation(summary = "An employee's documents (HR)")
    public List<EmployeeDocument> forEmployee(@PathVariable Long id) {
        return documents.forEmployee(id);
    }

    @PostMapping(value = "/hr/employees/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Share a letter with an employee, e.g. type OFFER_LETTER (HR)")
    public EmployeeDocument issue(@PathVariable Long id, @RequestParam DocumentType type, @RequestParam MultipartFile file) {
        return documents.issue(id, type, DocumentController.upload(file));
    }
}
