package com.hrportal.web;

import com.hrportal.domain.Role;
import com.hrportal.service.BusinessRuleException;
import com.hrportal.service.UserAccountService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Super Admin: logins, roles, lock/unlock and password resets. */
@Controller
@RequestMapping("/admin/users")
public class AdminController {

    private final UserAccountService accounts;

    public AdminController(UserAccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public String users(Model model) {
        model.addAttribute("users", accounts.findAll());
        model.addAttribute("roles", Role.values());
        return "admin/users";
    }

    @PostMapping("/{id}/role")
    public String changeRole(@PathVariable Long id, @RequestParam Role role, RedirectAttributes redirect) {
        return Flash.run(redirect, "Role updated", "/admin/users", () -> accounts.changeRole(id, role));
    }

    @PostMapping("/{id}/unlock")
    public String unlock(@PathVariable Long id, RedirectAttributes redirect) {
        return Flash.run(redirect, "Account unlocked", "/admin/users", () -> accounts.unlock(id));
    }

    @PostMapping("/{id}/enabled")
    public String setEnabled(@PathVariable Long id, @RequestParam boolean enabled, RedirectAttributes redirect) {
        return Flash.run(redirect, enabled ? "Account enabled" : "Account disabled", "/admin/users",
                () -> accounts.setEnabled(id, enabled));
    }

    @PostMapping("/{id}/reset-password")
    public String resetPassword(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            String temporary = accounts.resetPassword(id);
            redirect.addFlashAttribute("success", "Temporary password for " + accounts.get(id).getUsername() + ": "
                    + temporary + " (share it securely; they must change it at next sign-in)");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }
}
