package com.hrportal.web;

import com.hrportal.security.CurrentUser;
import com.hrportal.service.BusinessRuleException;
import com.hrportal.service.EmployeeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProfileController {

    private final CurrentUser currentUser;
    private final EmployeeService employees;

    public ProfileController(CurrentUser currentUser, EmployeeService employees) {
        this.currentUser = currentUser;
        this.employees = employees;
    }

    @GetMapping("/profile")
    public String view(Model model) {
        model.addAttribute("employee", currentUser.requireEmployee());
        return "account/profile";
    }

    @GetMapping("/profile/edit")
    public String edit(Model model) {
        model.addAttribute("employee", currentUser.requireEmployee());
        return "account/profile-edit";
    }

    @PostMapping("/profile/edit")
    public String save(@RequestParam String phone, @RequestParam String address,
                       @RequestParam String emergencyContactName, @RequestParam String emergencyContactPhone,
                       Model model, RedirectAttributes redirect) {
        var self = currentUser.requireEmployee();
        boolean first = !self.isProfileCompleted();
        try {
            employees.updateProfile(self, new EmployeeService.Profile(phone, address, emergencyContactName,
                    emergencyContactPhone));
        } catch (BusinessRuleException e) {
            model.addAttribute("error", e.getMessage());
            self.setPhone(phone);
            self.setAddress(address);
            self.setEmergencyContactName(emergencyContactName);
            self.setEmergencyContactPhone(emergencyContactPhone);
            model.addAttribute("employee", self);
            return "account/profile-edit";
        }
        redirect.addFlashAttribute("success", first ? "Profile completed. Welcome aboard!" : "Profile updated");
        return first ? "redirect:/" : "redirect:/profile";
    }
}
