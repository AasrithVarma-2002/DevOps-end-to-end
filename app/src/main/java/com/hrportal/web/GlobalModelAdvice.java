package com.hrportal.web;

import com.hrportal.security.CurrentUser;
import com.hrportal.service.NotificationService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
public class GlobalModelAdvice {

    private final CurrentUser currentUser;
    private final NotificationService notifications;

    public GlobalModelAdvice(CurrentUser currentUser, NotificationService notifications) {
        this.currentUser = currentUser;
        this.notifications = notifications;
    }

    @ModelAttribute("me")
    public CurrentUserView me() {
        return currentUser.principal()
                .map(p -> new CurrentUserView(currentUser.account().getDisplayName(), p.getRole(), p.getEmployeeId(),
                        notifications.unreadCount(p.getUserId())))
                .orElse(null);
    }
}
