package com.hrportal.web;

import com.hrportal.domain.EmployeeStatus;
import com.hrportal.domain.Role;
import com.hrportal.repository.UserAccountRepository;
import com.hrportal.security.CurrentUser;
import com.hrportal.service.BusinessRuleException;
import com.hrportal.service.DepartmentService;
import com.hrportal.service.EmployeeService;
import com.hrportal.service.LeaveService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** HR: employee directory, onboarding, job changes and offboarding. */
@Controller
@RequestMapping("/hr/employees")
public class HrEmployeeController {

    private final EmployeeService employees;
    private final DepartmentService departments;
    private final LeaveService leave;
    private final UserAccountRepository accounts;
    private final CurrentUser currentUser;
    private final Clock clock;

    public HrEmployeeController(EmployeeService employees, DepartmentService departments, LeaveService leave,
                                UserAccountRepository accounts, CurrentUser currentUser, Clock clock) {
        this.employees = employees;
        this.departments = departments;
        this.leave = leave;
        this.accounts = accounts;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q, @RequestParam(required = false) Long departmentId,
                       @RequestParam(required = false, defaultValue = "ACTIVE") String status, Model model) {
        EmployeeStatus s = "ALL".equals(status) ? null : EmployeeStatus.valueOf(status);
        model.addAttribute("employees", employees.search(q, departmentId, s));
        model.addAttribute("departments", departments.findAll());
        model.addAttribute("q", q);
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("status", status);
        return "hr/employees/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        EmployeeForm form = new EmployeeForm();
        form.setJoiningDate(LocalDate.now(clock));
        return form(model, form, true);
    }

    @PostMapping("/new")
    public String onboard(@ModelAttribute("form") EmployeeForm form, Model model, RedirectAttributes redirect) {
        try {
            var result = employees.onboard(form.toJobDetails(), form.getRole());
            // flash attributes live in the (database-backed) session, so only plain strings go in
            redirect.addFlashAttribute("onboardedName", result.employee().getFullName());
            redirect.addFlashAttribute("onboardedUsername", result.username());
            redirect.addFlashAttribute("onboardedPassword", result.temporaryPassword());
            return "redirect:/hr/employees/" + result.employee().getId();
        } catch (BusinessRuleException | AccessDeniedException e) {
            model.addAttribute("error", e.getMessage());
            return form(model, form, true);
        }
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        var e = employees.get(id);
        model.addAttribute("employee", e);
        model.addAttribute("account", accounts.findByEmployeeId(id).orElse(null));
        model.addAttribute("balances", leave.balances(e, LocalDate.now(clock).getYear()));
        model.addAttribute("requests", leave.history(id));
        model.addAttribute("reports", employees.directReports(id));
        return "hr/employees/view";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        return form(model, EmployeeForm.from(employees.get(id)), false);
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute("form") EmployeeForm form, Model model,
                         RedirectAttributes redirect) {
        try {
            employees.updateJobDetails(id, form.toJobDetails());
            redirect.addFlashAttribute("success", "Employee details saved");
            return "redirect:/hr/employees/" + id;
        } catch (BusinessRuleException e) {
            form.setId(id);
            model.addAttribute("error", e.getMessage());
            return form(model, form, false);
        }
    }

    @GetMapping("/{id}/offboard")
    public String offboardForm(@PathVariable Long id, Model model) {
        var e = employees.get(id);
        model.addAttribute("employee", e);
        model.addAttribute("reports", employees.directReports(id));
        model.addAttribute("today", LocalDate.now(clock));
        return "hr/employees/offboard";
    }

    @PostMapping("/{id}/offboard")
    public String offboard(@PathVariable Long id,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lastWorkingDay,
                           @RequestParam String reason, RedirectAttributes redirect) {
        try {
            var result = employees.offboard(id, lastWorkingDay, reason);
            redirect.addFlashAttribute("success", result.employee().getFullName() + " has been offboarded. Login disabled, "
                    + result.leaveCancelled() + " leave request(s) cancelled"
                    + (result.requestsMovedToHr() > 0 ? ", " + result.requestsMovedToHr() + " team request(s) moved to HR" : "")
                    + (result.reportsWithoutManager() > 0 ? ". Assign a new manager to their "
                    + result.reportsWithoutManager() + " direct report(s)." : "."));
            return "redirect:/hr/employees/" + id;
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/hr/employees/" + id + "/offboard";
        }
    }

    private String form(Model model, EmployeeForm form, boolean isNew) {
        model.addAttribute("form", form);
        model.addAttribute("isNew", isNew);
        model.addAttribute("departments", departments.findAll());
        model.addAttribute("managers", employees.active().stream()
                .filter(e -> form.getId() == null || !e.getId().equals(form.getId())).toList());
        // HR can create Employees and Managers; only a Super Admin can create HR Admins
        model.addAttribute("roles", Arrays.stream(Role.values())
                .filter(r -> currentUser.hasAtLeast(Role.SUPER_ADMIN) || r.ordinal() <= Role.MANAGER.ordinal())
                .toList());
        return "hr/employees/form";
    }
}
