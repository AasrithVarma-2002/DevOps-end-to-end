package com.hrportal.api;

import com.hrportal.domain.Employee;
import com.hrportal.domain.LeaveRequest;
import com.hrportal.security.CurrentUser;
import com.hrportal.service.EmployeeService;
import com.hrportal.service.LeaveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/team")
@Tag(name = "Manager", description = "Direct reports and first-step leave approvals (Manager role and above)")
public class TeamApi {

    private final CurrentUser currentUser;
    private final EmployeeService employees;
    private final LeaveService leave;

    public TeamApi(CurrentUser currentUser, EmployeeService employees, LeaveService leave) {
        this.currentUser = currentUser;
        this.employees = employees;
        this.leave = leave;
    }

    public record Decision(String comment) {
    }

    @GetMapping
    @Operation(summary = "My direct reports")
    public List<Employee> team() {
        return employees.directReports(currentUser.requireEmployee().getId());
    }

    @GetMapping("/approvals")
    @Operation(summary = "Requests waiting for my decision")
    public List<LeaveRequest> approvals() {
        return leave.awaitingManager(currentUser.requireEmployee().getId());
    }

    @PostMapping("/approvals/{id}/approve")
    @Operation(summary = "Approve (leave over 5 days then goes to HR)")
    public LeaveRequest approve(@PathVariable Long id, @RequestBody(required = false) Decision decision) {
        return leave.managerDecision(id, currentUser.requireEmployee(), true, decision == null ? null : decision.comment());
    }

    @PostMapping("/approvals/{id}/reject")
    @Operation(summary = "Reject")
    public LeaveRequest reject(@PathVariable Long id, @RequestBody(required = false) Decision decision) {
        return leave.managerDecision(id, currentUser.requireEmployee(), false, decision == null ? null : decision.comment());
    }
}
