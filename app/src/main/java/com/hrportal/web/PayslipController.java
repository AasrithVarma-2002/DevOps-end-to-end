package com.hrportal.web;

import com.hrportal.security.CurrentUser;
import com.hrportal.service.PayrollService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/** Employee self-service: my payslips and their PDFs. */
@Controller
@RequestMapping("/payslips")
public class PayslipController {

    private final CurrentUser currentUser;
    private final PayrollService payroll;

    public PayslipController(CurrentUser currentUser, PayrollService payroll) {
        this.currentUser = currentUser;
        this.payroll = payroll;
    }

    @GetMapping
    public String myPayslips(Model model) {
        model.addAttribute("payslips", payroll.myPayslips(currentUser.requireEmployee()));
        return "payslips/my-payslips";
    }

    /** Own payslips for employees, any payslip for HR (drafts as a preview). */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return pdfResponse(payroll.pdf(id), "payslip-" + id + ".pdf");
    }

    static ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(filename).build().toString())
                .body(pdf);
    }
}
