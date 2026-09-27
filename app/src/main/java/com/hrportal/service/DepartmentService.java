package com.hrportal.service;

import com.hrportal.domain.Department;
import com.hrportal.domain.EmployeeStatus;
import com.hrportal.repository.DepartmentRepository;
import com.hrportal.repository.EmployeeRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DepartmentService {

    private final DepartmentRepository departments;
    private final EmployeeRepository employees;
    private final AuditService audit;

    public DepartmentService(DepartmentRepository departments, EmployeeRepository employees, AuditService audit) {
        this.departments = departments;
        this.employees = employees;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Department> findAll() {
        return departments.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Department get(Long id) {
        return departments.findById(id).orElseThrow(() -> new NotFoundException("Department " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public long activeHeadcount(Long id) {
        return employees.countByDepartmentIdAndStatus(id, EmployeeStatus.ACTIVE);
    }

    public Department create(String name, String location) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessRuleException("Department name is required");
        }
        if (departments.existsByNameIgnoreCase(trimmed)) {
            throw new BusinessRuleException("A department named '" + trimmed + "' already exists");
        }
        Department saved = departments.save(new Department(trimmed, blankToNull(location)));
        audit.record("DEPARTMENT_CREATED", "Department", saved.getId(), trimmed);
        return saved;
    }

    public Department update(Long id, String name, String location) {
        Department department = get(id);
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessRuleException("Department name is required");
        }
        if (!department.getName().equalsIgnoreCase(trimmed) && departments.existsByNameIgnoreCase(trimmed)) {
            throw new BusinessRuleException("A department named '" + trimmed + "' already exists");
        }
        var changes = new AuditService.Changes()
                .add("name", department.getName(), trimmed)
                .add("location", department.getLocation(), blankToNull(location));
        department.setName(trimmed);
        department.setLocation(blankToNull(location));
        if (!changes.isEmpty()) {
            audit.record("DEPARTMENT_UPDATED", "Department", id, changes.toString());
        }
        return department;
    }

    public void delete(Long id) {
        Department department = get(id);
        // Former employees keep their department for history, so they block deletion too
        if (employees.countByDepartmentId(id) > 0) {
            throw new BusinessRuleException("Cannot delete " + department.getName()
                    + ": it still has employees. Move them to another department first.");
        }
        departments.delete(department);
        audit.record("DEPARTMENT_DELETED", "Department", id, department.getName());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
