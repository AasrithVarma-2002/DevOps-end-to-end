package com.hrportal.service;

import com.hrportal.domain.Employee;
import com.hrportal.domain.Notification;
import com.hrportal.domain.Role;
import com.hrportal.domain.UserAccount;
import com.hrportal.repository.NotificationRepository;
import com.hrportal.repository.UserAccountRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** In-app notifications (the bell icon). Email delivery is added in stage 4. */
@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notifications;
    private final UserAccountRepository accounts;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, UserAccountRepository accounts, Clock clock) {
        this.notifications = notifications;
        this.accounts = accounts;
        this.clock = clock;
    }

    public void notify(UserAccount recipient, String message, String link) {
        notifications.save(new Notification(recipient, message, link, LocalDateTime.now(clock)));
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
