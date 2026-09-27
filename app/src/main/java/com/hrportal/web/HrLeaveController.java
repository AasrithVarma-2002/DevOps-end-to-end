package com.hrportal.web;

import com.hrportal.domain.LeaveStatus;
import com.hrportal.service.AuditService;
import com.hrportal.service.LeaveService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** HR: final approvals, company-wide leave and the audit log. */
@Controller
@RequestMapping("/hr")
public class HrLeaveController {

    private final LeaveService leave;
    private final AuditService audit;

    public HrLeaveController(LeaveService leave, AuditService audit) {
        this.leave = leave;
        this.audit = audit;
    }

    @GetMapping("/approvals")
    public String approvals(Model model) {
        model.addAttribute("requests", leave.awaitingHr());
        return "hr/approvals";
    }

    @PostMapping("/approvals/{id}/approve")
    public String approve(@PathVariable Long id, @RequestParam(required = false) String comment,
                          @RequestParam(defaultValue = "/hr/approvals") String back, RedirectAttributes redirect) {
        return Flash.run(redirect, "Request approved", safe(back), () -> leave.hrDecision(id, true, comment));
    }

    @PostMapping("/approvals/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam(required = false) String comment,
                         @RequestParam(defaultValue = "/hr/approvals") String back, RedirectAttributes redirect) {
        return Flash.run(redirect, "Request rejected", safe(back), () -> leave.hrDecision(id, false, comment));
    }

    @GetMapping("/leave")
    public String allLeave(@RequestParam(required = false) LeaveStatus status, Model model) {
        model.addAttribute("requests", leave.all(status));
        model.addAttribute("status", status);
        model.addAttribute("statuses", LeaveStatus.values());
        return "hr/leave";
    }

    @PostMapping("/leave/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes redirect) {
        return Flash.run(redirect, "Leave request cancelled", "/hr/leave", () -> leave.cancel(id));
    }

    @GetMapping("/audit")
    public String audit(@RequestParam(required = false) String q, @RequestParam(required = false) String entityType,
                        @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("entries", audit.search(q, entityType, page));
        model.addAttribute("q", q);
        model.addAttribute("entityType", entityType);
        model.addAttribute("entityTypes", new String[] {"Employee", "LeaveRequest", "UserAccount", "Department", "Holiday"});
        return "hr/audit";
    }

    /** Only allow redirects back into the HR area (prevents open redirects). */
    private static String safe(String back) {
        return back != null && back.startsWith("/hr/") && !back.contains("//") ? back : "/hr/approvals";
    }
}
