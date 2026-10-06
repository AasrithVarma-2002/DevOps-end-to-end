package com.hrportal.jobs;

import com.hrportal.service.ReminderService;
import com.hrportal.service.ResignationService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * When the reminders run (India time). Every pod has this schedule; @SchedulerLock lets only
 * one of them run each job (the lock is a row in the shedlock table). lockAtLeastFor keeps the
 * lock a little longer, so a pod whose clock is slightly behind can't run the job again.
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderJobs {

    private static final Logger log = LoggerFactory.getLogger(ReminderJobs.class);

    private final ReminderService reminders;
    private final ResignationService resignations;

    public ReminderJobs(ReminderService reminders, ResignationService resignations) {
        this.reminders = reminders;
        this.resignations = resignations;
    }

    /** Weekdays 09:30: managers and HR with leave requests waiting. */
    @Scheduled(cron = "${app.jobs.pending-leave-cron:0 30 9 * * MON-FRI}", zone = "${app.time-zone}")
    @SchedulerLock(name = "pendingLeaveReminder", lockAtLeastFor = "PT1M", lockAtMostFor = "PT10M")
    public void pendingLeave() {
        log.info("Job pendingLeaveReminder: {} reminder(s) sent", reminders.remindPendingLeave());
    }

    /** Weekdays 20:00: people who forgot to check out. */
    @Scheduled(cron = "${app.jobs.missed-checkout-cron:0 0 20 * * MON-FRI}", zone = "${app.time-zone}")
    @SchedulerLock(name = "missedCheckOutReminder", lockAtLeastFor = "PT1M", lockAtMostFor = "PT10M")
    public void missedCheckOuts() {
        log.info("Job missedCheckOutReminder: {} reminder(s) sent", reminders.remindMissedCheckOuts());
    }

    /** The 5th of every month, 10:00: last month's payroll not run or not finalized. */
    @Scheduled(cron = "${app.jobs.payroll-cron:0 0 10 5 * *}", zone = "${app.time-zone}")
    @SchedulerLock(name = "payrollReminder", lockAtLeastFor = "PT1M", lockAtMostFor = "PT10M")
    public void payrollDue() {
        log.info("Job payrollReminder: HR reminded = {}", reminders.remindPayrollDue());
    }

    /** Every night 00:15: people whose last working day has passed are offboarded. */
    @Scheduled(cron = "${app.jobs.resignation-cron:0 15 0 * * *}", zone = "${app.time-zone}")
    @SchedulerLock(name = "completeResignations", lockAtLeastFor = "PT1M", lockAtMostFor = "PT10M")
    public void completeResignations() {
        log.info("Job completeResignations: {} resignation(s) completed", resignations.completeDue());
    }
}
