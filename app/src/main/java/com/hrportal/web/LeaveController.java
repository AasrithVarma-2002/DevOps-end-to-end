package com.hrportal.web;

import com.hrportal.domain.LeaveType;
import com.hrportal.security.CurrentUser;
import com.hrportal.service.BusinessRuleException;
import com.hrportal.service.HolidayService;
import com.hrportal.service.LeaveService;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Employee self-service leave. */
@Controller
@RequestMapping("/leave")
public class LeaveController {

    private final CurrentUser currentUser;
    private final LeaveService leave;
    private final HolidayService holidays;
    private final Clock clock;

    public LeaveController(CurrentUser currentUser, LeaveService leave, HolidayService holidays, Clock clock) {
        this.currentUser = currentUser;
        this.leave = leave;
        this.holidays = holidays;
        this.clock = clock;
    }

    @GetMapping
    public String myLeave(@RequestParam(required = false) Integer year, Model model) {
        var me = currentUser.requireEmployee();
        int y = year == null ? LocalDate.now(clock).getYear() : year;
        model.addAttribute("year", y);
        model.addAttribute("balances", leave.balances(me, y));
        model.addAttribute("requests", leave.history(me.getId()));
        model.addAttribute("today", LocalDate.now(clock));
        return "leave/my-leave";
    }

    @GetMapping("/apply")
    public String applyForm(Model model) {
        var me = currentUser.requireEmployee();
        LocalDate today = LocalDate.now(clock);
        model.addAttribute("types", LeaveType.values());
        model.addAttribute("balances", leave.balances(me, today.getYear()));
        model.addAttribute("holidays", holidays.forYear(today.getYear()));
        model.addAttribute("today", today);
        model.addAttribute("threshold", LeaveService.HR_APPROVAL_THRESHOLD);
        return "leave/apply";
    }

    @PostMapping
    public String apply(@RequestParam LeaveType type,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                        @RequestParam(defaultValue = "false") boolean halfDay,
                        @RequestParam(required = false) String reason, Model model, RedirectAttributes redirect) {
        var me = currentUser.requireEmployee();
        LocalDate end = halfDay || endDate == null ? startDate : endDate;
        try {
            var request = leave.apply(me, new LeaveService.Application(type, startDate, end, halfDay, reason));
            redirect.addFlashAttribute("success", "Request submitted for "
                    + request.getDays().stripTrailingZeros().toPlainString() + " working day(s). "
                    + (request.getManager() != null ? "Sent to " + request.getManagerName() + "." : "Sent to HR."));
            return "redirect:/leave";
        } catch (BusinessRuleException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("form", new FormValues(type, startDate, end, halfDay, reason));
            return applyForm(model);
        }
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes redirect) {
        return Flash.run(redirect, "Leave request cancelled", "/leave", () -> leave.cancel(id));
    }

    /** Re-fills the form after a validation error. */
    public record FormValues(LeaveType type, LocalDate startDate, LocalDate endDate, boolean halfDay, String reason) {
    }
}
