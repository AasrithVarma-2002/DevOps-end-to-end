package com.hrportal.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.hrportal.email.LoggingEmailService;
import com.hrportal.email.SesEmailService;
import org.junit.jupiter.api.Test;

/** SES only with a sender; it must start without AWS access (credentials are resolved on first send). */
class EmailConfigTest {

    @Test
    void sesIsUsedOnlyWithASender() {
        var config = new EmailConfig();
        assertThat(config.emailService("ses", "hr@example.com", "ap-south-1")).isInstanceOf(SesEmailService.class);
        assertThat(config.emailService("ses", "", "ap-south-1")).isInstanceOf(LoggingEmailService.class);
        assertThat(config.emailService("log", "hr@example.com", "ap-south-1")).isInstanceOf(LoggingEmailService.class);
    }
}
