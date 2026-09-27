package com.hrportal.web;

import com.hrportal.service.DepartmentService;
import com.hrportal.service.HolidayService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** HR: departments and the public holiday calendar. */
@Controller
@RequestMapping("/hr")
public class HrSetupController {

    private final DepartmentService departments;
    private final HolidayService holidays;
    private final Clock clock;

    public HrSetupController(DepartmentService departments, HolidayService holidays, Clock clock) {
        this.departments = departments;
        this.holidays = holidays;
        this.clock = clock;
    }

    @GetMapping("/departments")
    public String departments(Model model) {
        Map<Object, Long> headcount = new LinkedHashMap<>();
        departments.findAll().forEach(d -> headcount.put(d, departments.activeHeadcount(d.getId())));
        model.addAttribute("headcount", headcount);
        return "hr/departments";
    }

    @PostMapping("/departments")
    public String createDepartment(@RequestParam String name, @RequestParam(required = false) String location,
                                   RedirectAttributes redirect) {
        return Flash.run(redirect, "Department " + name + " created", "/hr/departments",
                () -> departments.create(name, location));
    }

    @PostMapping("/departments/{id}")
    public String updateDepartment(@PathVariable Long id, @RequestParam String name,
                                   @RequestParam(required = false) String location, RedirectAttributes redirect) {
        return Flash.run(redirect, "Department saved", "/hr/departments",
                () -> departments.update(id, name, location));
    }

    @PostMapping("/departments/{id}/delete")
    public String deleteDepartment(@PathVariable Long id, RedirectAttributes redirect) {
        return Flash.run(redirect, "Department deleted", "/hr/departments", () -> departments.delete(id));
    }

    @GetMapping("/holidays")
    public String holidays(@RequestParam(required = false) Integer year, Model model) {
        int y = year == null ? LocalDate.now(clock).getYear() : year;
        model.addAttribute("year", y);
        model.addAttribute("holidays", holidays.forYear(y));
        return "hr/holidays";
    }

    @PostMapping("/holidays")
    public String addHoliday(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             @RequestParam String name, RedirectAttributes redirect) {
        return Flash.run(redirect, name + " added", "/hr/holidays?year=" + date.getYear(),
                () -> holidays.add(date, name));
    }

    @PostMapping("/holidays/{id}/delete")
    public String deleteHoliday(@PathVariable Long id, @RequestParam int year, RedirectAttributes redirect) {
        return Flash.run(redirect, "Holiday removed", "/hr/holidays?year=" + year, () -> holidays.delete(id));
    }
}
