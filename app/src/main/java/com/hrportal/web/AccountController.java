package com.hrportal.web;

import com.hrportal.security.CurrentUser;
import com.hrportal.service.BusinessRuleException;
import com.hrportal.service.NotificationService;
import com.hrportal.service.UserAccountService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountController {

    private final CurrentUser currentUser;
    private final UserAccountService accounts;
    private final NotificationService notifications;

    public AccountController(CurrentUser currentUser, UserAccountService accounts, NotificationService notifications) {
        this.currentUser = currentUser;
        this.accounts = accounts;
        this.notifications = notifications;
    }

    @GetMapping("/account/password")
    public String passwordForm(Model model) {
        model.addAttribute("forced", currentUser.account().isMustChangePassword());
        return "account/password";
    }

    @PostMapping("/account/password")
    public String changePassword(@RequestParam String currentPassword, @RequestParam String newPassword,
                                 @RequestParam String confirmPassword, Model model, RedirectAttributes redirect) {
        try {
            accounts.changePassword(currentUser.account().getId(), currentPassword, newPassword, confirmPassword);
        } catch (BusinessRuleException e) {
            model.addAttribute("error", e.getMessage());
            return passwordForm(model);
        }
        redirect.addFlashAttribute("success", "Your password has been changed");
        return "redirect:/";
    }

    @GetMapping("/notifications")
    public String notifications(Model model) {
        Long userId = currentUser.account().getId();
        model.addAttribute("notifications", notifications.recent(userId));
        return "account/notifications";
    }

    @PostMapping("/notifications/read-all")
    public String markAllRead() {
        notifications.markAllRead(currentUser.account().getId());
        return "redirect:/notifications";
    }
}
