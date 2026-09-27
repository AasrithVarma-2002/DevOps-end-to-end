package com.hrportal.service;

import com.hrportal.domain.Department;
import com.hrportal.domain.Employee;
import com.hrportal.domain.EmployeeStatus;
import com.hrportal.domain.Role;
import com.hrportal.repository.EmployeeRepository;
import com.hrportal.security.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The employee lifecycle: onboarding, profile, job changes and offboarding. */
@Service
@Transactional
public class EmployeeService {

    private final EmployeeRepository employees;
    private final DepartmentService departments;
    private final UserAccountService accounts;
    private final LeaveService leave;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CurrentUser currentUser;
    private final Clock clock;

    public EmployeeService(EmployeeRepository employees, DepartmentService departments, UserAccountService accounts,
                           LeaveService leave, NotificationService notifications, AuditService audit,
                           CurrentUser currentUser, Clock clock) {
        this.employees = employees;
        this.departments = departments;
        this.accounts = accounts;
        this.leave = leave;
        this.notifications = notifications;
        this.audit = audit;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /** Job details HR enters for a new hire or edits later. */
    public record JobDetails(String employeeCode, String firstName, String lastName, String email, String phone,
                             String jobTitle, Long departmentId, Long managerId, BigDecimal salary,
                             LocalDate joiningDate) {
    }

    public record Profile(String phone, String address, String emergencyContactName, String emergencyContactPhone) {
    }

    /** What HR needs to hand over to the new hire. */
    public record OnboardingResult(Employee employee, String username, String temporaryPassword) {
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<Employee> search(String q, Long departmentId, EmployeeStatus status) {
        return employees.search(q == null ? null : q.trim(), departmentId, status);
    }

    @Transactional(readOnly = true)
    public List<Employee> active() {
        return employees.findByStatusOrderByFirstNameAsc(EmployeeStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Employee> directReports(Long managerId) {
        return employees.findByManagerIdAndStatusOrderByFirstNameAsc(managerId, EmployeeStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public Employee get(Long id) {
        return employees.findById(id).orElseThrow(() -> new NotFoundException("Employee " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public long activeCount() {
        return employees.countByStatus(EmployeeStatus.ACTIVE);
    }

    // ------------------------------------------------------------------ onboarding

    public OnboardingResult onboard(JobDetails details, Role role) {
        if (role == null) {
            role = Role.EMPLOYEE;
        }
        if ((role == Role.HR_ADMIN || role == Role.SUPER_ADMIN) && !currentUser.hasAtLeast(Role.SUPER_ADMIN)) {
            throw new AccessDeniedException("Only a Super Admin can grant the " + role.getLabel() + " role");
        }
        validate(details, null);
        String email = details.email().trim().toLowerCase();
        if (employees.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("Email " + email + " is already in use");
        }
        if (employees.existsByEmployeeCodeIgnoreCase(details.employeeCode().trim())) {
            throw new BusinessRuleException("Employee code " + details.employeeCode().trim() + " is already in use");
        }

        Employee e = new Employee();
        applyJobDetails(e, details);
        e.setStatus(EmployeeStatus.ACTIVE);
        e.setProfileCompleted(false);
        employees.save(e);

        String temporaryPassword = accounts.createForEmployee(e, role);
        leave.ensureBalances(e, Math.max(LocalDate.now(clock).getYear(), e.getJoiningDate().getYear()));

        audit.record("EMPLOYEE_ONBOARDED", "Employee", e.getId(), e.getFullName() + " (" + e.getEmployeeCode()
                + "), " + e.getJobTitle() + ", " + e.getDepartment().getName()
                + ", manager: " + (e.getManager() == null ? "none" : e.getManager().getFullName())
                + ", role: " + role.getLabel() + ", joining " + e.getJoiningDate());
        notifications.notifyEmployee(e, "Welcome to HR Portal Pro, " + e.getFirstName()
                + "! Please complete your profile.", "/profile/edit");
        if (e.getManager() != null) {
            notifications.notifyEmployee(e.getManager(), e.getFullName() + " is joining your team on "
                    + e.getJoiningDate(), "/team");
        }
        return new OnboardingResult(e, email, temporaryPassword);
    }

    // ------------------------------------------------------------------ HR edits

    public Employee updateJobDetails(Long id, JobDetails details) {
        Employee e = get(id);
        if (!e.isActive()) {
            throw new BusinessRuleException("Employees who have left cannot be edited");
        }
        validate(details, e);
        String email = details.email().trim().toLowerCase();
        if (!e.getEmail().equalsIgnoreCase(email) && employees.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("Email " + email + " is already in use");
        }
        if (!e.getEmployeeCode().equalsIgnoreCase(details.employeeCode().trim())
                && employees.existsByEmployeeCodeIgnoreCase(details.employeeCode().trim())) {
            throw new BusinessRuleException("Employee code " + details.employeeCode().trim() + " is already in use");
        }

        Department newDept = departments.get(details.departmentId());
        Employee newManager = details.managerId() == null ? null : get(details.managerId());
        var changes = new AuditService.Changes()
                .add("employee code", e.getEmployeeCode(), details.employeeCode().trim())
                .add("first name", e.getFirstName(), details.firstName().trim())
                .add("last name", e.getLastName(), details.lastName().trim())
                .add("email", e.getEmail(), email)
                .add("phone", e.getPhone(), details.phone())
                .add("job title", e.getJobTitle(), details.jobTitle().trim())
                .add("department", e.getDepartment().getName(), newDept.getName())
                .add("manager", e.getManagerName(), newManager == null ? null : newManager.getFullName())
                .add("salary", e.getSalary(), details.salary())
                .add("joining date", e.getJoiningDate(), details.joiningDate());

        if (!e.getEmail().equalsIgnoreCase(email)) {
            accounts.renameForEmployee(e, email);
        }
        applyJobDetails(e, details);
        if (!changes.isEmpty()) {
            audit.record("EMPLOYEE_UPDATED", "Employee", id, e.getFullName() + " - " + changes);
        }
        return e;
    }

    // ------------------------------------------------------------------ self service

    public Employee updateProfile(Employee self, Profile profile) {
        Employee e = get(self.getId());
        if (isBlank(profile.phone()) || isBlank(profile.address()) || isBlank(profile.emergencyContactName())
                || isBlank(profile.emergencyContactPhone())) {
            throw new BusinessRuleException("Phone, address and an emergency contact are all required");
        }
        var changes = new AuditService.Changes()
                .add("phone", e.getPhone(), profile.phone().trim())
                .add("address", e.getAddress(), profile.address().trim())
                .add("emergency contact", e.getEmergencyContactName(), profile.emergencyContactName().trim())
                .add("emergency phone", e.getEmergencyContactPhone(), profile.emergencyContactPhone().trim());
        boolean firstCompletion = !e.isProfileCompleted();
        e.setPhone(profile.phone().trim());
        e.setAddress(profile.address().trim());
        e.setEmergencyContactName(profile.emergencyContactName().trim());
        e.setEmergencyContactPhone(profile.emergencyContactPhone().trim());
        e.setProfileCompleted(true);
        if (firstCompletion || !changes.isEmpty()) {
            audit.record(firstCompletion ? "PROFILE_COMPLETED" : "PROFILE_UPDATED", "Employee", e.getId(),
                    e.getFullName() + (changes.isEmpty() ? "" : " - " + changes));
        }
        return e;
    }

    // ------------------------------------------------------------------ offboarding

    public record OffboardingResult(Employee employee, int leaveCancelled, int requestsMovedToHr, int reportsWithoutManager) {
    }

    /**
     * Ends employment: disables the login, cancels leave not yet taken, and moves any
     * requests waiting for this person (as a manager) to HR.
     */
    public OffboardingResult offboard(Long id, LocalDate lastWorkingDay, String reason) {
        Employee e = get(id);
        if (!e.isActive()) {
            throw new BusinessRuleException(e.getFullName() + " has already left");
        }
        if (e.getId().equals(currentUser.principal().map(p -> p.getEmployeeId()).orElse(null))) {
            throw new BusinessRuleException("You cannot offboard yourself");
        }
        if (lastWorkingDay == null || isBlank(reason)) {
            throw new BusinessRuleException("Last working day and reason are required");
        }
        if (lastWorkingDay.isBefore(e.getJoiningDate())) {
            throw new BusinessRuleException("The last working day cannot be before the joining date");
        }

        e.setStatus(EmployeeStatus.EXITED);
        e.setLastWorkingDay(lastWorkingDay);
        e.setExitReason(reason.trim());
        accounts.disableForExit(e);
        int cancelled = leave.cancelForExit(e, lastWorkingDay);
        int moved = leave.reroutePendingToHr(e);
        int orphans = directReports(e.getId()).size();

        audit.record("EMPLOYEE_OFFBOARDED", "Employee", id, e.getFullName() + ", last working day " + lastWorkingDay
                + ", reason: " + reason.trim() + "; login disabled; " + cancelled + " leave request(s) cancelled"
                + (moved > 0 ? "; " + moved + " team request(s) moved to HR" : ""));
        if (orphans > 0) {
            notifications.notifyHr(e.getFullName() + " has left and had " + orphans
                    + " direct report(s). Assign them a new manager.", "/hr/employees?q=");
        }
        return new OffboardingResult(e, cancelled, moved, orphans);
    }

    // ------------------------------------------------------------------ helpers

    private void validate(JobDetails d, Employee existing) {
        if (isBlank(d.employeeCode()) || isBlank(d.firstName()) || isBlank(d.lastName()) || isBlank(d.email())
                || isBlank(d.jobTitle()) || d.departmentId() == null || d.salary() == null || d.joiningDate() == null) {
            throw new BusinessRuleException("Code, name, email, job title, department, salary and joining date are required");
        }
        if (!d.email().trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BusinessRuleException("Enter a valid email address");
        }
        if (d.salary().signum() <= 0) {
            throw new BusinessRuleException("Salary must be greater than zero");
        }
        if (d.managerId() != null) {
            Employee manager = get(d.managerId());
            if (!manager.isActive()) {
                throw new BusinessRuleException(manager.getFullName() + " has left and cannot be a manager");
            }
            if (existing != null) {
                // walk up the chain to make sure we don't create a reporting loop
                for (Employee m = manager; m != null; m = m.getManager()) {
                    if (m.getId().equals(existing.getId())) {
                        throw new BusinessRuleException("That would create a reporting loop: "
                                + manager.getFullName() + " reports to " + existing.getFullName());
                    }
                }
            }
        }
    }

    private void applyJobDetails(Employee e, JobDetails d) {
        e.setEmployeeCode(d.employeeCode().trim());
        e.setFirstName(d.firstName().trim());
        e.setLastName(d.lastName().trim());
        e.setEmail(d.email().trim().toLowerCase());
        e.setPhone(isBlank(d.phone()) ? null : d.phone().trim());
        e.setJobTitle(d.jobTitle().trim());
        e.setDepartment(departments.get(d.departmentId()));
        e.setManager(d.managerId() == null ? null : get(d.managerId()));
        e.setSalary(d.salary());
        e.setJoiningDate(d.joiningDate());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
