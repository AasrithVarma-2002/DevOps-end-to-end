package com.hrportal.web;

import com.hrportal.service.ReportService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** HR: CSV downloads (attendance, leave, payroll register). */
@Controller
@RequestMapping("/hr/reports")
public class HrReportController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ReportService reports;
    private final Clock clock;

    public HrReportController(ReportService reports, Clock clock) {
        this.reports = reports;
        this.clock = clock;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("lastMonth", YearMonth.now(clock).minusMonths(1));
        model.addAttribute("thisYear", LocalDate.now(clock).getYear());
        return "hr/reports";
    }

    @GetMapping("/attendance.csv")
    public ResponseEntity<String> attendance(@RequestParam(required = false) String month) {
        YearMonth m = AttendanceController.parseMonth(month, clock);
        return csv(reports.attendance(m), "attendance-" + m + ".csv");
    }

    @GetMapping("/leave.csv")
    public ResponseEntity<String> leave(@RequestParam(required = false) Integer year) {
        int y = year == null ? LocalDate.now(clock).getYear() : year;
        return csv(reports.leave(y), "leave-" + y + ".csv");
    }

    @GetMapping("/payroll.csv")
    public ResponseEntity<String> payroll(@RequestParam(required = false) String month) {
        YearMonth m = AttendanceController.parseMonth(month, clock);
        return csv(reports.payrollRegister(m), "payroll-register-" + m + ".csv");
    }

    private static ResponseEntity<String> csv(String body, String filename) {
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
