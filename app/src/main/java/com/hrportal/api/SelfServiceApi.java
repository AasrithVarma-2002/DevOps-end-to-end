package com.hrportal.api;

import com.hrportal.domain.Employee;
import com.hrportal.domain.Holiday;
import com.hrportal.domain.LeaveRequest;
import com.hrportal.domain.LeaveType;
import com.hrportal.security.CurrentUser;
import com.hrportal.service.HolidayService;
import com.hrportal.service.LeaveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Self service", description = "The signed-in employee's own profile and leave")
public class SelfServiceApi {

    private final CurrentUser currentUser;
    private final LeaveService leave;
    private final HolidayService holidays;
    private final Clock clock;

    public SelfServiceApi(CurrentUser currentUser, LeaveService leave, HolidayService holidays, Clock clock) {
        this.currentUser = currentUser;
        this.leave = leave;
        this.holidays = holidays;
        this.clock = clock;
    }

    public record Me(Employee employee, String role, List<LeaveService.BalanceView> balances) {
    }

    public record LeaveApplication(LeaveType type, LocalDate startDate, LocalDate endDate, boolean halfDay,
                                   String reason) {
    }

    @GetMapping("/me")
    @Operation(summary = "My profile, role and this year's leave balances")
    public Me me() {
        Employee e = currentUser.requireEmployee();
        return new Me(e, currentUser.account().getRole().name(), leave.balances(e, LocalDate.now(clock).getYear()));
    }

    @GetMapping("/leave")
    @Operation(summary = "My leave requests, newest first")
    public List<LeaveRequest> myLeave() {
        return leave.history(currentUser.requireEmployee().getId());
    }

    @PostMapping("/leave")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Apply for leave (routed to my manager, or HR if I have none)")
    public LeaveRequest apply(@RequestBody LeaveApplication body) {
        LocalDate end = body.halfDay() || body.endDate() == null ? body.startDate() : body.endDate();
        return leave.apply(currentUser.requireEmployee(),
                new LeaveService.Application(body.type(), body.startDate(), end, body.halfDay(), body.reason()));
    }

    @PostMapping("/leave/{id}/cancel")
    @Operation(summary = "Cancel one of my requests (approved leave: only before it starts)")
    public LeaveRequest cancel(@PathVariable Long id) {
        return leave.cancel(id);
    }

    @GetMapping("/holidays")
    @Operation(summary = "Public holidays for a year")
    public List<Holiday> holidays(@RequestParam(required = false) Integer year) {
        return holidays.forYear(year == null ? LocalDate.now(clock).getYear() : year);
    }
}
