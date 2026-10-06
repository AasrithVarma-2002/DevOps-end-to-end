package com.hrportal.repository;

import com.hrportal.domain.PayrollRun;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {

    boolean existsByMonth(YearMonth month);

    Optional<PayrollRun> findByMonth(YearMonth month);

    List<PayrollRun> findAllByOrderByMonthDesc();
}
