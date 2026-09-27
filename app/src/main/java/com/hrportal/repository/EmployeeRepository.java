package com.hrportal.repository;

import com.hrportal.domain.Employee;
import com.hrportal.domain.EmployeeStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @Query("""
            select e from Employee e
            where (:q is null or :q = ''
                   or lower(e.firstName) like lower(concat('%', :q, '%'))
                   or lower(e.lastName) like lower(concat('%', :q, '%'))
                   or lower(e.email) like lower(concat('%', :q, '%'))
                   or lower(e.employeeCode) like lower(concat('%', :q, '%'))
                   or lower(e.jobTitle) like lower(concat('%', :q, '%')))
              and (:departmentId is null or e.department.id = :departmentId)
              and (:status is null or e.status = :status)
            order by e.lastName, e.firstName
            """)
    List<Employee> search(@Param("q") String q, @Param("departmentId") Long departmentId,
                          @Param("status") EmployeeStatus status);

    List<Employee> findByStatusOrderByFirstNameAsc(EmployeeStatus status);

    List<Employee> findByManagerIdAndStatusOrderByFirstNameAsc(Long managerId, EmployeeStatus status);

    long countByStatus(EmployeeStatus status);

    long countByDepartmentIdAndStatus(Long departmentId, EmployeeStatus status);

    long countByDepartmentId(Long departmentId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmployeeCodeIgnoreCase(String employeeCode);
}
