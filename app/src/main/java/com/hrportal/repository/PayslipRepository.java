package com.hrportal.repository;

import com.hrportal.domain.PayrollStatus;
import com.hrportal.domain.Payslip;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {

    @Query("select p from Payslip p where p.run.id = :runId order by p.employee.firstName, p.employee.lastName")
    List<Payslip> findByRun(@Param("runId") Long runId);

    @Query("""
            select p from Payslip p
            where p.employee.id = :employeeId and p.run.status = :status
            order by p.run.month desc
            """)
    List<Payslip> findByEmployeeAndStatus(@Param("employeeId") Long employeeId, @Param("status") PayrollStatus status);

    @Modifying
    @Query("delete from Payslip p where p.run.id = :runId")
    void deleteByRun(@Param("runId") Long runId);
}
