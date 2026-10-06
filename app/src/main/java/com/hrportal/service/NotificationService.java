package com.hrportal.service;

import com.hrportal.domain.Employee;
import com.hrportal.email.EmailRequested;
import com.hrportal.domain.Notification;
import com.hrportal.domain.Role;
import com.hrportal.domain.UserAccount;
import com.hrportal.repository.NotificationRepository;
import com.hrportal.repository.UserAccountRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app notifications (the bell icon). Each one is also emailed to the recipient's login
 * address once the change is committed (EmailDispatcher).
 */
@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notifications;
    private final UserAccountRepository accounts;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final String baseUrl;

    public NotificationService(NotificationRepository notifications, UserAccountRepository accounts, Clock clock,
                               ApplicationEventPublisher events, @Value("${app.email.base-url:}") String baseUrl) {
        this.notifications = notifications;
        this.accounts = accounts;
        this.clock = clock;
        this.events = events;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    public void notify(UserAccount recipient, String message, String link) {
        notifications.save(new Notification(recipient, message, link, LocalDateTime.now(clock)));
        String to = recipient.getUsername();
        if (isDeliverable(to)) {
            events.publishEvent(new EmailRequested(to, subject(message), body(recipient, message, link)));
        }
    }

    /** ".local" is reserved and never reaches a mailbox (e.g. admin@hrportal.local, the demo users). */
    static boolean isDeliverable(String address) {
        return address != null && address.contains("@") && !address.toLowerCase().endsWith(".local");
    }

    private static String subject(String message) {
        String s = "HR Portal Pro: " + message;
        return s.length() > 120 ? s.substring(0, 117) + "..." : s;
    }

    private String body(UserAccount recipient, String message, String link) {
        String name = recipient.getEmployee() == null ? "there" : recipient.getEmployee().getFirstName();
        String where = baseUrl.isEmpty() || link == null
                ? "Sign in to HR Portal Pro to see the details."
                : "Open it here: " + baseUrl + link;
        return "Hello " + name + ",\n\n" + message + "\n\n" + where
                + "\n\nThis message was sent automatically by HR Portal Pro.";
    }

    public void notifyEmployee(Employee employee, String message, String link) {
        accounts.findByEmployeeId(employee.getId()).ifPresent(a -> notify(a, message, link));
    }

    /** Every enabled HR Admin and Super Admin. */
    public void notifyHr(String message, String link) {
        accounts.findByRoleInAndEnabledTrue(List.of(Role.HR_ADMIN, Role.SUPER_ADMIN))
                .forEach(a -> notify(a, message, link));
    }

    @Transactional(readOnly = true)
    public List<Notification> recent(Long userId) {
        return notifications.findTop50ByRecipientIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notifications.countByRecipientIdAndReadFalse(userId);
    }

    public void markAllRead(Long userId) {
        notifications.markAllRead(userId);
    }
}
