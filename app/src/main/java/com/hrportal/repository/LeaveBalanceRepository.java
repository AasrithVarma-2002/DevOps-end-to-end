package com.hrportal.repository;

import com.hrportal.domain.LeaveBalance;
import com.hrportal.domain.LeaveType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    List<LeaveBalance> findByEmployeeIdAndYearOrderByTypeAsc(Long employeeId, int year);

    Optional<LeaveBalance> findByEmployeeIdAndYearAndType(Long employeeId, int year, LeaveType type);

    boolean existsByEmployeeIdAndYear(Long employeeId, int year);
}
