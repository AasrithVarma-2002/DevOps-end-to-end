package com.hrportal.web;

import com.hrportal.security.CurrentUser;
import com.hrportal.service.EmployeeService;
import com.hrportal.service.LeaveService;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final CurrentUser currentUser;
    private final LeaveService leave;
    private final EmployeeService employees;
    private final Clock clock;

    public HomeController(CurrentUser currentUser, LeaveService leave, EmployeeService employees, Clock clock) {
        this.currentUser = currentUser;
        this.leave = leave;
        this.employees = employees;
        this.clock = clock;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /** One dashboard; sections appear according to the user's role. */
    @GetMapping("/")
    public String dashboard(Model model) {
        LocalDate today = LocalDate.now(clock);
        model.addAttribute("today", today);
        currentUser.employee().ifPresent(e -> {
            model.addAttribute("employee", e);
            model.addAttribute("balances", leave.balances(e, today.getYear()));
            model.addAttribute("upcoming", leave.history(e.getId()).stream()
                    .filter(r -> !r.getEndDate().isBefore(today)
                            && (r.getStatus().isPending() || r.getStatus().name().equals("APPROVED")))
                    .toList());
            if (currentUser.hasAtLeast(com.hrportal.domain.Role.MANAGER)) {
                model.addAttribute("teamPending", leave.countAwaitingManager(e.getId()));
                model.addAttribute("teamOut", leave.teamOutToday(e.getId()));
                model.addAttribute("teamSize", employees.directReports(e.getId()).size());
            }
        });
        if (currentUser.hasAtLeast(com.hrportal.domain.Role.HR_ADMIN)) {
            model.addAttribute("hrPending", leave.countAwaitingHr());
            model.addAttribute("activeEmployees", employees.activeCount());
            model.addAttribute("outToday", leave.outToday());
        }
        return "dashboard";
    }
}
