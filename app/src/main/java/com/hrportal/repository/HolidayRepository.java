package com.hrportal.repository;

import com.hrportal.domain.Holiday;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    List<Holiday> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);

    boolean existsByDate(LocalDate date);
}
