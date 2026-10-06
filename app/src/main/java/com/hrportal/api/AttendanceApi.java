package com.hrportal.api;

import com.hrportal.domain.AttendanceRecord;
import com.hrportal.domain.DayStatus;
import com.hrportal.security.CurrentUser;
import com.hrportal.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Attendance", description = "Check-in/out (everyone), team view (Manager), corrections (HR)")
public class AttendanceApi {

    private final CurrentUser currentUser;
    private final AttendanceService attendance;
    private final Clock clock;

    public AttendanceApi(CurrentUser currentUser, AttendanceService attendance, Clock clock) {
        this.currentUser = currentUser;
        this.attendance = attendance;
        this.clock = clock;
    }

    public record DayView(LocalDate date, DayStatus status, String detail) {
        static DayView of(AttendanceService.Day d) {
            return new DayView(d.date(), d.status(), d.detail());
        }
    }

    public record MonthView(YearMonth month, AttendanceService.Summary summary, List<DayView> days) {
    }

    public record TeamMemberView(Long employeeId, String name, DayView today, AttendanceService.Summary month) {
    }

    public record Correction(Long employeeId, LocalDate date, LocalTime checkIn, LocalTime checkOut, String note) {
    }

    @PostMapping("/attendance/check-in")
    @Operation(summary = "Check in for today (once a day)")
    public AttendanceRecord checkIn() {
        return attendance.checkIn(currentUser.requireEmployee());
    }

    @PostMapping("/attendance/check-out")
    @Operation(summary = "Check out for today")
    public AttendanceRecord checkOut() {
        return attendance.checkOut(currentUser.requireEmployee());
    }

    @GetMapping("/attendance")
    @Operation(summary = "My attendance for a month (default: this month), e.g. ?month=2026-03")
    public MonthView myMonth(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        var m = attendance.month(currentUser.requireEmployee(), month == null ? YearMonth.now(clock) : month);
        return new MonthView(m.month(), m.summary(), m.days().stream().map(DayView::of).toList());
    }

    @GetMapping("/team/attendance")
    @Operation(summary = "My direct reports: today's status and this month's totals")
    public List<TeamMemberView> team() {
        return attendance.team(currentUser.requireEmployee().getId()).stream()
                .map(t -> new TeamMemberView(t.employee().getId(), t.employee().getFullName(),
                        DayView.of(t.today()), t.month()))
                .toList();
    }

    @PostMapping("/hr/attendance/corrections")
    @Operation(summary = "Add or fix a past day's attendance (HR)")
    public AttendanceRecord correct(@RequestBody Correction body) {
        return attendance.correct(body.employeeId(), body.date(), body.checkIn(), body.checkOut(), body.note());
    }
}
