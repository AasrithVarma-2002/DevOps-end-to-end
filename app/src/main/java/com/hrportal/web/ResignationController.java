package com.hrportal.web;

import com.hrportal.security.CurrentUser;
import com.hrportal.service.ResignationService;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Employee self-service: resign, follow it, withdraw it while HR hasn't decided. */
@Controller
@RequestMapping("/resignation")
public class ResignationController {

    private final CurrentUser currentUser;
    private final ResignationService resignations;
    private final Clock clock;

    public ResignationController(CurrentUser currentUser, ResignationService resignations, Clock clock) {
        this.currentUser = currentUser;
        this.resignations = resignations;
        this.clock = clock;
    }

    @GetMapping
    public String page(Model model) {
        var me = currentUser.requireEmployee();
        model.addAttribute("current", resignations.current(me.getId()).orElse(null));
        model.addAttribute("history", resignations.history(me.getId()));
        model.addAttribute("defaultLastDay", resignations.defaultLastDay());
        model.addAttribute("noticeDays", resignations.noticeDays());
        model.addAttribute("today", LocalDate.now(clock));
        return "resignation/my-resignation";
    }

    @PostMapping
    public String submit(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lastDay,
                         @RequestParam(required = false) String reason, RedirectAttributes redirect) {
        return Flash.run(redirect, "Resignation submitted. Your manager and HR have been told.", "/resignation",
                () -> resignations.submit(currentUser.requireEmployee(), lastDay, reason));
    }

    @PostMapping("/withdraw")
    public String withdraw(RedirectAttributes redirect) {
        return Flash.run(redirect, "Resignation withdrawn", "/resignation",
                () -> resignations.withdraw(currentUser.requireEmployee()));
    }
}
