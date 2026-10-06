package com.hrportal.service;

import com.hrportal.domain.AttendanceRecord;
import com.hrportal.domain.Employee;
import com.hrportal.domain.LeaveRequest;
import com.hrportal.domain.LeaveStatus;
import com.hrportal.repository.AttendanceRecordRepository;
import com.hrportal.repository.LeaveRequestRepository;
import com.hrportal.repository.PayrollRunRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the scheduled jobs do (ReminderJobs decides when). Each reminder is an in-app
 * notification, and therefore also an email. Methods return how many people were reminded.
 */
@Service
@Transactional
public class ReminderService {

    private final LeaveRequestRepository leaveRequests;
    private final AttendanceRecordRepository attendance;
    private final PayrollRunRepository payrollRuns;
    private final NotificationService notifications;
    private final Clock clock;

    public ReminderService(LeaveRequestRepository leaveRequests, AttendanceRecordRepository attendance,
                           PayrollRunRepository payrollRuns, NotificationService notifications, Clock clock) {
        this.leaveRequests = leaveRequests;
        this.attendance = attendance;
        this.payrollRuns = payrollRuns;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Managers with requests waiting for them, and HR if any request waits for HR. */
    public int remindPendingLeave() {
        Map<Employee, Long> perManager = leaveRequests.findByStatusOrderByCreatedAtAsc(LeaveStatus.PENDING_MANAGER)
                .stream()
                .filter(r -> r.getManager() != null && r.getManager().isActive())
                .collect(Collectors.groupingBy(LeaveRequest::getManager, LinkedHashMap::new, Collectors.counting()));
        perManager.forEach((manager, count) -> notifications.notifyEmployee(manager,
                count + " leave request" + (count == 1 ? " is" : "s are") + " waiting for your approval",
                "/team/approvals"));

        long forHr = leaveRequests.countByStatus(LeaveStatus.PENDING_HR);
        if (forHr > 0) {
            notifications.notifyHr(forHr + " leave request" + (forHr == 1 ? " is" : "s are")
                    + " waiting for HR approval", "/hr/approvals");
        }
        return perManager.size() + (forHr > 0 ? 1 : 0);
    }

    /** Everyone who checked in today and is still checked in. */
    public int remindMissedCheckOuts() {
        List<AttendanceRecord> open = attendance.findByWorkDate(LocalDate.now(clock)).stream()
                .filter(AttendanceRecord::isOpen)
                .toList();
        open.forEach(r -> notifications.notifyEmployee(r.getEmployee(), "You checked in at "
                + r.getCheckIn().toLocalTime().withSecond(0) + " today but haven't checked out yet", "/attendance"));
        return open.size();
    }

    /** HR, if last month's payroll hasn't been run or finalized yet. */
    public boolean remindPayrollDue() {
        YearMonth lastMonth = YearMonth.now(clock).minusMonths(1);
        String month = PayrollService.label(lastMonth);
        var run = payrollRuns.findByMonth(lastMonth);
        if (run.isEmpty()) {
            notifications.notifyHr("Payroll for " + month + " hasn't been run yet", "/hr/payroll");
            return true;
        }
        if (run.get().isDraft()) {
            notifications.notifyHr("Payroll for " + month + " is still a draft. Review and finalize it.",
                    "/hr/payroll/" + run.get().getId());
            return true;
        }
        return false;
    }
}
