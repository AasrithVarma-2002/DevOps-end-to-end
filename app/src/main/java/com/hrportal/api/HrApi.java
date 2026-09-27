package com.hrportal.api;

import com.hrportal.domain.Department;
import com.hrportal.domain.Employee;
import com.hrportal.domain.EmployeeStatus;
import com.hrportal.domain.LeaveRequest;
import com.hrportal.domain.Role;
import com.hrportal.service.DepartmentService;
import com.hrportal.service.EmployeeService;
import com.hrportal.service.LeaveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
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
@RequestMapping("/api/hr")
@Tag(name = "HR", description = "Employee lifecycle and final leave approvals (HR Admin role and above)")
public class HrApi {

    private final EmployeeService employees;
    private final DepartmentService departments;
    private final LeaveService leave;

    public HrApi(EmployeeService employees, DepartmentService departments, LeaveService leave) {
        this.employees = employees;
        this.departments = departments;
        this.leave = leave;
    }

    public record NewHire(String employeeCode, String firstName, String lastName, String email, String phone,
                          String jobTitle, Long departmentId, Long managerId, BigDecimal salary,
                          LocalDate joiningDate, Role role) {
    }

    public record Onboarded(Long employeeId, String username, String temporaryPassword) {
    }

    public record Offboarding(LocalDate lastWorkingDay, String reason) {
    }

    public record Decision(String comment) {
    }

    @GetMapping("/employees")
    @Operation(summary = "Search employees")
    public List<Employee> employees(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) Long departmentId,
                                    @RequestParam(required = false) EmployeeStatus status) {
        return employees.search(q, departmentId, status);
    }

    @GetMapping("/employees/{id}")
    public Employee employee(@PathVariable Long id) {
        return employees.get(id);
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Onboard a new hire: creates the employee, login and leave balances")
    public Onboarded onboard(@RequestBody NewHire h) {
        var result = employees.onboard(new EmployeeService.JobDetails(h.employeeCode(), h.firstName(), h.lastName(),
                h.email(), h.phone(), h.jobTitle(), h.departmentId(), h.managerId(), h.salary(), h.joiningDate()), h.role());
        return new Onboarded(result.employee().getId(), result.username(), result.temporaryPassword());
    }

    @PostMapping("/employees/{id}/offboard")
    @Operation(summary = "Offboard: disables the login and cancels leave not yet taken")
    public Employee offboard(@PathVariable Long id, @RequestBody Offboarding body) {
        return employees.offboard(id, body.lastWorkingDay(), body.reason()).employee();
    }

    @GetMapping("/departments")
    public List<Department> departments() {
        return departments.findAll();
    }

    @GetMapping("/approvals")
    @Operation(summary = "Requests waiting for final HR approval")
    public List<LeaveRequest> approvals() {
        return leave.awaitingHr();
    }

    @PostMapping("/approvals/{id}/approve")
    public LeaveRequest approve(@PathVariable Long id, @RequestBody(required = false) Decision d) {
        return leave.hrDecision(id, true, d == null ? null : d.comment());
    }

    @PostMapping("/approvals/{id}/reject")
    public LeaveRequest reject(@PathVariable Long id, @RequestBody(required = false) Decision d) {
        return leave.hrDecision(id, false, d == null ? null : d.comment());
    }
}
