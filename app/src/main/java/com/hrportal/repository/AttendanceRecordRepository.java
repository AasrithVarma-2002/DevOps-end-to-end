package com.hrportal.repository;

import com.hrportal.domain.AttendanceRecord;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findByEmployee_IdAndWorkDate(Long employeeId, LocalDate workDate);

    List<AttendanceRecord> findByEmployee_IdAndWorkDateBetweenOrderByWorkDateAsc(Long employeeId, LocalDate from,
                                                                                LocalDate to);

    List<AttendanceRecord> findByWorkDate(LocalDate workDate);

    List<AttendanceRecord> findByEmployee_IdInAndWorkDateBetween(Collection<Long> employeeIds, LocalDate from,
                                                                 LocalDate to);
}
