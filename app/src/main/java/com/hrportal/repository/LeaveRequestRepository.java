package com.hrportal.repository;

import com.hrportal.domain.LeaveRequest;
import com.hrportal.domain.LeaveStatus;
import com.hrportal.domain.LeaveType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByEmployee_IdOrderByStartDateDesc(Long employeeId);

    List<LeaveRequest> findByManagerIdAndStatusOrderByCreatedAtAsc(Long managerId, LeaveStatus status);

    List<LeaveRequest> findByStatusOrderByCreatedAtAsc(LeaveStatus status);

    List<LeaveRequest> findByEmployee_IdAndStatusIn(Long employeeId, Collection<LeaveStatus> statuses);

    long countByManagerIdAndStatus(Long managerId, LeaveStatus status);

    long countByStatus(LeaveStatus status);

    @Query("""
            select l from LeaveRequest l
            where (:status is null or l.status = :status)
            order by l.createdAt desc
            """)
    List<LeaveRequest> findAllByStatus(@Param("status") LeaveStatus status);

    @Query("""
            select count(l) > 0 from LeaveRequest l
            where l.employee.id = :employeeId
              and l.status in :statuses
              and l.startDate <= :endDate
              and l.endDate >= :startDate
            """)
    boolean existsOverlapping(@Param("employeeId") Long employeeId,
                              @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate,
                              @Param("statuses") Collection<LeaveStatus> statuses);

    /** Days already requested but not yet decided, so they can't be booked twice. */
    @Query("""
            select coalesce(sum(l.days), 0) from LeaveRequest l
            where l.employee.id = :employeeId and l.type = :type
              and l.status in :statuses
              and l.startDate >= :yearStart and l.startDate <= :yearEnd
            """)
    BigDecimal sumDays(@Param("employeeId") Long employeeId, @Param("type") LeaveType type,
                       @Param("statuses") Collection<LeaveStatus> statuses,
                       @Param("yearStart") LocalDate yearStart, @Param("yearEnd") LocalDate yearEnd);

    @Query("""
            select l from LeaveRequest l
            where l.status = com.hrportal.domain.LeaveStatus.APPROVED
              and l.startDate <= :day and l.endDate >= :day
            order by l.employee.firstName
            """)
    List<LeaveRequest> findApprovedOn(@Param("day") LocalDate day);

    @Query("""
            select l from LeaveRequest l
            where l.status = com.hrportal.domain.LeaveStatus.APPROVED
              and l.employee.manager.id = :managerId
              and l.startDate <= :day and l.endDate >= :day
            order by l.employee.firstName
            """)
    List<LeaveRequest> findTeamApprovedOn(@Param("managerId") Long managerId, @Param("day") LocalDate day);

    /** Approved leave of one employee that touches the date range (for the attendance calendar). */
    @Query("""
            select l from LeaveRequest l
            where l.status = com.hrportal.domain.LeaveStatus.APPROVED
              and l.employee.id = :employeeId
              and l.startDate <= :to and l.endDate >= :from
            """)
    List<LeaveRequest> findApprovedBetween(@Param("employeeId") Long employeeId, @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);
}
