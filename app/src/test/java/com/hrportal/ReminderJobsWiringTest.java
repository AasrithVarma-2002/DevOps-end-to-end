package com.hrportal;

import static org.assertj.core.api.Assertions.assertThat;

import com.hrportal.jobs.ReminderJobs;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** With jobs on (as in production): the jobs, the lock provider and the lock row all work. */
@SpringBootTest(properties = "app.jobs.enabled=true")
@Import(TestClockConfig.class)
class ReminderJobsWiringTest {

    @Autowired ReminderJobs jobs;
    @Autowired LockProvider lockProvider;
    @Autowired JdbcTemplate jdbc;

    @Test
    void jobsRunThroughTheLock() {
        jobs.payrollDue();   // called through the ShedLock proxy
        Integer rows = jdbc.queryForObject("select count(*) from shedlock where name = 'payrollReminder'", Integer.class);
        assertThat(rows).isEqualTo(1);
        assertThat(lockProvider).isNotNull();

        jobs.completeResignations();
        Integer exitRows = jdbc.queryForObject("select count(*) from shedlock where name = 'completeResignations'", Integer.class);
        assertThat(exitRows).isEqualTo(1);
    }
}
