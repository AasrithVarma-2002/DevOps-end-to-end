package com.hrportal.config;

import com.hrportal.email.LoggingEmailService;
import com.hrportal.email.SesEmailService;
import com.hrportal.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * app.email.type "ses" with a sender address = real email through SES; anything else (or no
 * sender yet) = log only. A missing sender is not fatal: the app runs, emails are just logged.
 */
@Configuration
public class EmailConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

    @Bean
    EmailService emailService(@Value("${app.email.type}") String type,
                              @Value("${app.email.from}") String from,
                              @Value("${app.email.region}") String region) {
        if ("ses".equals(type) && from != null && !from.isBlank()) {
            log.info("Email: Amazon SES in {}, from {}", region, from);
            return new SesEmailService(SesV2Client.builder().region(Region.of(region)).build(), from.trim());
        }
        if ("ses".equals(type)) {
            log.warn("Email: app.email.type=ses but no sender (APP_EMAIL_FROM) yet; emails are only logged");
        }
        return new LoggingEmailService();
    }
}
