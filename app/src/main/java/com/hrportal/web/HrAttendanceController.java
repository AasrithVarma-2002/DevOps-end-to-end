package com.hrportal.web;

import com.hrportal.service.AttendanceService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** HR: everyone's attendance for a day, and corrections for past days. */
@Controller
@RequestMapping("/hr/attendance")
public class HrAttendanceController {

    private final AttendanceService attendance;
    private final Clock clock;

    public HrAttendanceController(AttendanceService attendance, Clock clock) {
        this.attendance = attendance;
        this.clock = clock;
    }

    @GetMapping
    public String day(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                      Model model) {
        LocalDate d = date == null ? LocalDate.now(clock) : date;
        model.addAttribute("date", d);
        model.addAttribute("rows", attendance.everyoneOn(d));
        model.addAttribute("canCorrect", d.isBefore(LocalDate.now(clock)));
        return "hr/attendance";
    }

    @PostMapping("/correct")
    public String correct(@RequestParam Long employeeId,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime checkIn,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime checkOut,
                          @RequestParam(required = false) String note, RedirectAttributes redirect) {
        return Flash.run(redirect, "Attendance corrected", "/hr/attendance?date=" + date,
                () -> attendance.correct(employeeId, date, checkIn, checkOut, note));
    }
}
