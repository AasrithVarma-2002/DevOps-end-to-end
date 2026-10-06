package com.hrportal.web;

import com.hrportal.service.BusinessRuleException;
import com.hrportal.service.PayrollService;
import java.time.Clock;
import java.time.YearMonth;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** HR: payroll runs for each month, review, recalculate and finalize. */
@Controller
@RequestMapping("/hr/payroll")
public class HrPayrollController {

    private final PayrollService payroll;
    private final Clock clock;

    public HrPayrollController(PayrollService payroll, Clock clock) {
        this.payroll = payroll;
        this.clock = clock;
    }

    @GetMapping
    public String runs(Model model) {
        model.addAttribute("runs", payroll.runs());
        model.addAttribute("thisMonth", YearMonth.now(clock));
        return "hr/payroll/runs";
    }

    @PostMapping
    public String create(@RequestParam String month, RedirectAttributes redirect) {
        try {
            var run = payroll.create(AttendanceController.parseMonth(month, clock));
            redirect.addFlashAttribute("success", "Payroll for " + PayrollService.label(run.getMonth())
                    + " calculated. Review it, then finalize.");
            return "redirect:/hr/payroll/" + run.getId();
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/hr/payroll";
        }
    }

    @GetMapping("/{id}")
    public String run(@PathVariable Long id, Model model) {
        model.addAttribute("summary", payroll.summary(id));
        model.addAttribute("payslips", payroll.payslips(id));
        return "hr/payroll/run";
    }

    @PostMapping("/{id}/recalculate")
    public String recalculate(@PathVariable Long id, RedirectAttributes redirect) {
        return Flash.run(redirect, "Payslips recalculated", "/hr/payroll/" + id, () -> payroll.recalculate(id));
    }

    @PostMapping("/{id}/finalize")
    public String finalizeRun(@PathVariable Long id, RedirectAttributes redirect) {
        return Flash.run(redirect, "Payroll finalized. Payslips are stored and employees have been notified.",
                "/hr/payroll/" + id, () -> payroll.finalizeRun(id));
    }
}
