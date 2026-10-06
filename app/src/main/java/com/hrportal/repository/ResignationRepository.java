package com.hrportal.repository;

import com.hrportal.domain.Resignation;
import com.hrportal.domain.ResignationStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResignationRepository extends JpaRepository<Resignation, Long> {

    @Query("select r from Resignation r where r.employee.id = :employeeId order by r.submittedAt desc, r.id desc")
    List<Resignation> findByEmployee(@Param("employeeId") Long employeeId);

    @Query("select r from Resignation r where r.status in :statuses order by r.submittedAt asc, r.id asc")
    List<Resignation> findByStatusIn(@Param("statuses") Collection<ResignationStatus> statuses);

    @Query("select r from Resignation r where r.status in :statuses order by r.decidedAt desc, r.id desc")
    List<Resignation> findRecentByStatusIn(@Param("statuses") Collection<ResignationStatus> statuses,
                                           org.springframework.data.domain.Pageable page);

    /** Accepted resignations whose last working day is before the given day: due to be completed. */
    @Query("select r from Resignation r where r.status = :status and r.lastWorkingDay < :day order by r.lastWorkingDay asc")
    List<Resignation> findDue(@Param("status") ResignationStatus status, @Param("day") LocalDate day);
}
