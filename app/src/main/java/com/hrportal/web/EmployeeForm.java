package com.hrportal.web;

import com.hrportal.domain.Employee;
import com.hrportal.domain.Role;
import com.hrportal.service.EmployeeService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** Backing object for the onboarding and edit-employee forms. */
public class EmployeeForm {

    private Long id;
    private String employeeCode;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String jobTitle;
    private Long departmentId;
    private Long managerId;
    private BigDecimal salary;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate joiningDate;
    private Role role = Role.EMPLOYEE;

    public static EmployeeForm from(Employee e) {
        EmployeeForm f = new EmployeeForm();
        f.id = e.getId();
        f.employeeCode = e.getEmployeeCode();
        f.firstName = e.getFirstName();
        f.lastName = e.getLastName();
        f.email = e.getEmail();
        f.phone = e.getPhone();
        f.jobTitle = e.getJobTitle();
        f.departmentId = e.getDepartment().getId();
        f.managerId = e.getManager() == null ? null : e.getManager().getId();
        f.salary = e.getSalary();
        f.joiningDate = e.getJoiningDate();
        return f;
    }

    public EmployeeService.JobDetails toJobDetails() {
        return new EmployeeService.JobDetails(employeeCode, firstName, lastName, email, phone, jobTitle,
                departmentId, managerId, salary, joiningDate);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Long getManagerId() { return managerId; }
    public void setManagerId(Long managerId) { this.managerId = managerId; }
    public BigDecimal getSalary() { return salary; }
    public void setSalary(BigDecimal salary) { this.salary = salary; }
    public LocalDate getJoiningDate() { return joiningDate; }
    public void setJoiningDate(LocalDate joiningDate) { this.joiningDate = joiningDate; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
}
