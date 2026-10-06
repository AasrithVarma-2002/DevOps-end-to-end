package com.hrportal.web;

import com.hrportal.domain.DocumentType;
import com.hrportal.security.CurrentUser;
import com.hrportal.service.BusinessRuleException;
import com.hrportal.service.EmployeeDocumentService;
import com.hrportal.storage.Dispositions;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Employee self-service: my documents, uploads, and opening a stored file (own, or anyone's for HR). */
@Controller
@RequestMapping("/documents")
public class DocumentController {

    private final CurrentUser currentUser;
    private final EmployeeDocumentService documents;

    public DocumentController(CurrentUser currentUser, EmployeeDocumentService documents) {
        this.currentUser = currentUser;
        this.documents = documents;
    }

    @GetMapping
    public String myDocuments(Model model) {
        var me = currentUser.requireEmployee();
        model.addAttribute("checklist", documents.checklist(me));
        model.addAttribute("documents", documents.forEmployee(me.getId()));
        model.addAttribute("types", DocumentType.employeeTypes());
        model.addAttribute("maxMb", EmployeeDocumentService.MAX_BYTES / (1024 * 1024));
        return "documents/my-documents";
    }

    @PostMapping
    public String upload(@RequestParam(required = false) DocumentType type,
                         @RequestParam(required = false) MultipartFile file, RedirectAttributes redirect) {
        return Flash.run(redirect, "Uploaded. HR will verify it and you'll get a notification.", "/documents",
                () -> documents.upload(currentUser.requireEmployee(), type, upload(file)));
    }

    /** In AWS: a redirect to a 5-minute S3 link. Locally: the file itself. */
    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long id) {
        var download = documents.download(id);
        if (download.link() != null) {
            return ResponseEntity.status(HttpStatus.FOUND).location(download.link()).build();
        }
        var d = download.document();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        Dispositions.inline(d.getFileName()))
                .body(download.content());
    }

    public static EmployeeDocumentService.Upload upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Choose a file to upload");
        }
        try {
            return new EmployeeDocumentService.Upload(file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
    }
}
