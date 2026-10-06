package com.hrportal.email;

import com.hrportal.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends notification emails only after the change that caused them is committed, so a rolled
 * back action never emails anyone, and an email failure (SES down, unverified address in the
 * sandbox) never undoes the action. Outside a transaction (scheduled jobs) it sends right away.
 */
@Component
public class EmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);

    private final EmailService email;

    public EmailDispatcher(EmailService email) {
        this.email = email;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEmailRequested(EmailRequested e) {
        try {
            email.send(e.to(), e.subject(), e.body());
        } catch (RuntimeException ex) {
            log.warn("Could not email {} ({}): {}", e.to(), e.subject(), ex.getMessage());
        }
    }
}
