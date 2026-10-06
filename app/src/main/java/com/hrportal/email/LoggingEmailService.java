package com.hrportal.email;

import com.hrportal.service.EmailService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** No real sending: logs each email and keeps the last 100 (tests read them). */
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    public record SentEmail(String to, String subject, String body) {
    }

    private final ConcurrentLinkedDeque<SentEmail> sent = new ConcurrentLinkedDeque<>();

    @Override
    public void send(String to, String subject, String body) {
        log.info("Email (not sent, logging only) to {}: {}", to, subject);
        sent.addFirst(new SentEmail(to, subject, body));
        while (sent.size() > 100) {
            sent.removeLast();
        }
    }

    /** Newest first. */
    public List<SentEmail> sent() {
        return new ArrayList<>(sent);
    }
}
