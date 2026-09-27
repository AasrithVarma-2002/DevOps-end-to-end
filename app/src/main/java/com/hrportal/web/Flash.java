package com.hrportal.web;

import com.hrportal.service.BusinessRuleException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Runs an action and turns the outcome into a success or error banner on the next page. */
final class Flash {

    private Flash() {
    }

    static String run(RedirectAttributes redirect, String successMessage, String redirectTo, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("success", successMessage);
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + redirectTo;
    }
}
