package com.hrportal.web;

import com.hrportal.domain.DocumentType;
import com.hrportal.service.EmployeeDocumentService;
import com.hrportal.service.EmployeeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** HR: verify uploaded documents, see who is missing what, and issue letters to an employee. */
@Controller
@RequestMapping("/hr")
public class HrDocumentController {

    private final EmployeeDocumentService documents;
    private final EmployeeService employees;

    public HrDocumentController(EmployeeDocumentService documents, EmployeeService employees) {
        this.documents = documents;
        this.employees = employees;
    }

    @GetMapping("/documents")
    public String overview(Model model) {
        model.addAttribute("pending", documents.pending());
        model.addAttribute("checklists", documents.checklists());
        return "hr/documents";
    }

    @PostMapping("/documents/{id}/verify")
    public String verify(@PathVariable Long id, @RequestParam(required = false) String note,
                         @RequestParam(defaultValue = "/hr/documents") String back, RedirectAttributes redirect) {
        return Flash.run(redirect, "Document verified", safeBack(back), () -> documents.verify(id, note));
    }

    @PostMapping("/documents/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam(required = false) String note,
                         @RequestParam(defaultValue = "/hr/documents") String back, RedirectAttributes redirect) {
        return Flash.run(redirect, "Document rejected. The employee has been asked to upload it again.",
                safeBack(back), () -> documents.reject(id, note));
    }

    @GetMapping("/employees/{id}/documents")
    public String employeeDocuments(@PathVariable Long id, Model model) {
        var employee = employees.get(id);
        model.addAttribute("employee", employee);
        model.addAttribute("checklist", documents.checklist(employee));
        model.addAttribute("documents", documents.forEmployee(id));
        model.addAttribute("letterTypes", DocumentType.hrTypes());
        model.addAttribute("maxMb", EmployeeDocumentService.MAX_BYTES / (1024 * 1024));
        return "hr/employees/documents";
    }

    @PostMapping("/employees/{id}/documents")
    public String issue(@PathVariable Long id, @RequestParam(required = false) DocumentType type,
                        @RequestParam(required = false) MultipartFile file, RedirectAttributes redirect) {
        return Flash.run(redirect, "Letter shared with the employee", "/hr/employees/" + id + "/documents",
                () -> documents.issue(id, type, DocumentController.upload(file)));
    }

    /** Only redirect back to our own HR pages, never to another site. */
    static String safeBack(String back) {
        return back != null && back.matches("/hr/(documents|employees/\\d+/documents)") ? back : "/hr/documents";
    }
}
