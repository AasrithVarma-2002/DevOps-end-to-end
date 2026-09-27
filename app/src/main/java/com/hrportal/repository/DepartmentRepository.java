package com.hrportal.repository;

import com.hrportal.domain.Department;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
