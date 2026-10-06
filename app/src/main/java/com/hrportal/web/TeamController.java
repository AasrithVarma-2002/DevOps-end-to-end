package com.hrportal.web;

import com.hrportal.security.CurrentUser;
import com.hrportal.service.AttendanceService;
import com.hrportal.service.EmployeeService;
import com.hrportal.service.LeaveService;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Manager screens: direct reports, their attendance and leave approvals. */
@Controller
@RequestMapping("/team")
public class TeamController {

    private final CurrentUser currentUser;
    private final EmployeeService employees;
    private final LeaveService leave;
    private final AttendanceService attendance;
    private final Clock clock;

    public TeamController(CurrentUser currentUser, EmployeeService employees, LeaveService leave,
                          AttendanceService attendance, Clock clock) {
        this.currentUser = currentUser;
        this.employees = employees;
        this.leave = leave;
        this.attendance = attendance;
        this.clock = clock;
    }

    @GetMapping
    public String team(Model model) {
        var me = currentUser.requireEmployee();
        int year = LocalDate.now(clock).getYear();
        var reports = employees.directReports(me.getId());
        model.addAttribute("reports", reports);
        model.addAttribute("balances", reports.stream()
                .collect(java.util.stream.Collectors.toMap(e -> e.getId(), e -> leave.balances(e, year))));
        model.addAttribute("outToday", leave.teamOutToday(me.getId()));
        return "team/team";
    }

    @GetMapping("/attendance")
    public String attendance(Model model) {
        var me = currentUser.requireEmployee();
        model.addAttribute("members", attendance.team(me.getId()));
        model.addAttribute("month", java.time.YearMonth.now(clock));
        return "team/attendance";
    }

    @GetMapping("/approvals")
    public String approvals(Model model) {
        var me = currentUser.requireEmployee();
        model.addAttribute("requests", leave.awaitingManager(me.getId()));
        model.addAttribute("threshold", LeaveService.HR_APPROVAL_THRESHOLD);
        return "team/approvals";
    }

    @PostMapping("/approvals/{id}/approve")
    public String approve(@PathVariable Long id, @RequestParam(required = false) String comment, RedirectAttributes redirect) {
        var me = currentUser.requireEmployee();
        return Flash.run(redirect, "Request approved", "/team/approvals",
                () -> leave.managerDecision(id, me, true, comment));
    }

    @PostMapping("/approvals/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam(required = false) String comment, RedirectAttributes redirect) {
        var me = currentUser.requireEmployee();
        return Flash.run(redirect, "Request rejected", "/team/approvals",
                () -> leave.managerDecision(id, me, false, comment));
    }
}
