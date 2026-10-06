package com.hrportal.web;

import com.hrportal.security.CurrentUser;
import com.hrportal.service.AttendanceService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Employee self-service attendance: check in, check out, monthly calendar. */
@Controller
@RequestMapping("/attendance")
public class AttendanceController {

    private final CurrentUser currentUser;
    private final AttendanceService attendance;
    private final Clock clock;

    public AttendanceController(CurrentUser currentUser, AttendanceService attendance, Clock clock) {
        this.currentUser = currentUser;
        this.attendance = attendance;
        this.clock = clock;
    }

    @GetMapping
    public String myAttendance(@RequestParam(required = false) String month, Model model) {
        var me = currentUser.requireEmployee();
        YearMonth ym = parseMonth(month, clock);
        model.addAttribute("today", attendance.todayRecord(me));
        model.addAttribute("view", attendance.month(me, ym));
        model.addAttribute("prev", ym.minusMonths(1));
        model.addAttribute("next", ym.plusMonths(1));
        model.addAttribute("isCurrentMonth", ym.equals(YearMonth.now(clock)));
        model.addAttribute("todayDate", LocalDate.now(clock));
        return "attendance/my-attendance";
    }

    @PostMapping("/check-in")
    public String checkIn(RedirectAttributes redirect) {
        var me = currentUser.requireEmployee();
        return Flash.run(redirect, "Checked in. Have a good day!", "/attendance", () -> attendance.checkIn(me));
    }

    @PostMapping("/check-out")
    public String checkOut(RedirectAttributes redirect) {
        var me = currentUser.requireEmployee();
        return Flash.run(redirect, "Checked out. See you tomorrow!", "/attendance", () -> attendance.checkOut(me));
    }

    /** "2026-03" → March 2026; missing or invalid → this month. */
    static YearMonth parseMonth(String month, Clock clock) {
        if (month != null && !month.isBlank()) {
            try {
                return YearMonth.parse(month.trim());
            } catch (DateTimeParseException ignored) {
                // fall through to the current month
            }
        }
        return YearMonth.now(clock);
    }
}
