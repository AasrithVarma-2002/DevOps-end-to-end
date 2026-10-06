package com.hrportal.web;

import com.hrportal.service.ResignationService;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** HR: resignations waiting for a decision, people serving notice, and recent exits. */
@Controller
@RequestMapping("/hr/resignations")
public class HrResignationController {

    private final ResignationService resignations;
    private final Clock clock;

    public HrResignationController(ResignationService resignations, Clock clock) {
        this.resignations = resignations;
        this.clock = clock;
    }

    @GetMapping
    public String overview(Model model) {
        model.addAttribute("waiting", resignations.waitingForHr());
        model.addAttribute("serving", resignations.servingNotice());
        model.addAttribute("closed", resignations.recentlyClosed());
        model.addAttribute("svc", resignations);
        model.addAttribute("today", LocalDate.now(clock));
        return "hr/resignations";
    }

    @PostMapping("/{id}/accept")
    public String accept(@PathVariable Long id,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lastWorkingDay,
                         @RequestParam(required = false) String note, RedirectAttributes redirect) {
        return Flash.run(redirect, "Resignation accepted. The employee and their manager have been told.",
                "/hr/resignations", () -> resignations.accept(id, lastWorkingDay, note));
    }

    @PostMapping("/{id}/decline")
    public String decline(@PathVariable Long id, @RequestParam(required = false) String note, RedirectAttributes redirect) {
        return Flash.run(redirect, "Resignation declined. The employee has been told.", "/hr/resignations",
                () -> resignations.decline(id, note));
    }
}
