package com.hrportal.repository;

import com.hrportal.domain.DocumentStatus;
import com.hrportal.domain.EmployeeDocument;
import com.hrportal.domain.EmployeeStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Long> {

    @Query("select d from EmployeeDocument d where d.employee.id = :employeeId order by d.uploadedAt desc, d.id desc")
    List<EmployeeDocument> findByEmployee(@Param("employeeId") Long employeeId);

    @Query("select d from EmployeeDocument d where d.status = :status order by d.uploadedAt asc, d.id asc")
    List<EmployeeDocument> findByStatus(@Param("status") DocumentStatus status);

    long countByStatus(DocumentStatus status);

    /** Every document of the employees in a status, for the HR checklist overview. */
    @Query("select d from EmployeeDocument d where d.employee.status = :status order by d.uploadedAt desc, d.id desc")
    List<EmployeeDocument> findByEmployeeStatus(@Param("status") EmployeeStatus status);
}
