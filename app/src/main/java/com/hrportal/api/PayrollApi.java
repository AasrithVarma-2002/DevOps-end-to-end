package com.hrportal.api;

import com.hrportal.domain.PayrollRun;
import com.hrportal.domain.Payslip;
import com.hrportal.security.CurrentUser;
import com.hrportal.service.PayrollService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.YearMonth;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Payroll", description = "My payslips (everyone), payroll runs (HR)")
public class PayrollApi {

    private final CurrentUser currentUser;
    private final PayrollService payroll;

    public PayrollApi(CurrentUser currentUser, PayrollService payroll) {
        this.currentUser = currentUser;
        this.payroll = payroll;
    }

    public record NewRun(YearMonth month) {
    }

    public record RunView(Long id, YearMonth month, String status, int employees, java.math.BigDecimal totalGross,
                          java.math.BigDecimal totalNet) {
        static RunView of(PayrollService.RunSummary s) {
            return new RunView(s.run().getId(), s.run().getMonth(), s.run().getStatus().name(), s.employees(),
                    s.totalGross(), s.totalNet());
        }
    }

    @GetMapping("/payslips")
    @Operation(summary = "My finalized payslips, newest first")
    public List<Payslip> myPayslips() {
        return payroll.myPayslips(currentUser.requireEmployee());
    }

    @GetMapping(value = "/payslips/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Payslip PDF (own payslips; HR: any)")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("payslip-" + id + ".pdf").build().toString())
                .body(payroll.pdf(id));
    }

    @GetMapping("/hr/payroll")
    @Operation(summary = "Payroll runs, newest month first (HR)")
    public List<RunView> runs() {
        return payroll.runs().stream().map(RunView::of).toList();
    }

    @PostMapping("/hr/payroll")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Calculate payroll for a month, e.g. {\"month\":\"2026-03\"} (HR)")
    public RunView create(@RequestBody NewRun body) {
        PayrollRun run = payroll.create(body.month());
        return RunView.of(payroll.summary(run.getId()));
    }

    @GetMapping("/hr/payroll/{id}/payslips")
    @Operation(summary = "All payslips of a run (HR)")
    public List<Payslip> payslips(@PathVariable Long id) {
        return payroll.payslips(id);
    }

    @PostMapping("/hr/payroll/{id}/recalculate")
    @Operation(summary = "Recalculate a draft run (HR)")
    public RunView recalculate(@PathVariable Long id) {
        payroll.recalculate(id);
        return RunView.of(payroll.summary(id));
    }

    @PostMapping("/hr/payroll/{id}/finalize")
    @Operation(summary = "Finalize: store the PDFs and notify employees (HR)")
    public RunView finalizeRun(@PathVariable Long id) {
        payroll.finalizeRun(id);
        return RunView.of(payroll.summary(id));
    }
}
