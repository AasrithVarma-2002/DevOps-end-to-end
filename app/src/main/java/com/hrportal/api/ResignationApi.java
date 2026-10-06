package com.hrportal.api;

import com.hrportal.domain.Resignation;
import com.hrportal.security.CurrentUser;
import com.hrportal.service.ResignationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Resignation", description = "Resign and withdraw (everyone), accept or decline (HR)")
public class ResignationApi {

    private final CurrentUser currentUser;
    private final ResignationService resignations;

    public ResignationApi(CurrentUser currentUser, ResignationService resignations) {
        this.currentUser = currentUser;
        this.resignations = resignations;
    }

    public record NewResignation(LocalDate lastDay, String reason) {
    }

    public record Decision(LocalDate lastWorkingDay, String note) {
    }

    @GetMapping("/resignation")
    @Operation(summary = "My resignations, newest first")
    public List<Resignation> mine() {
        return resignations.history(currentUser.requireEmployee().getId());
    }

    @PostMapping("/resignation")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Resign, e.g. {\"lastDay\":\"2026-04-30\",\"reason\":\"Moving city\"}")
    public Resignation submit(@RequestBody NewResignation body) {
        return resignations.submit(currentUser.requireEmployee(), body.lastDay(), body.reason());
    }

    @PostMapping("/resignation/withdraw")
    @Operation(summary = "Withdraw my resignation while HR hasn't decided")
    public Resignation withdraw() {
        return resignations.withdraw(currentUser.requireEmployee());
    }

    @GetMapping("/hr/resignations")
    @Operation(summary = "Resignations waiting for HR (HR)")
    public List<Resignation> waiting() {
        return resignations.waitingForHr();
    }

    @PostMapping("/hr/resignations/{id}/accept")
    @Operation(summary = "Accept; lastWorkingDay defaults to the requested day (HR)")
    public Resignation accept(@PathVariable Long id, @RequestBody(required = false) Decision body) {
        return resignations.accept(id, body == null ? null : body.lastWorkingDay(), body == null ? null : body.note());
    }

    @PostMapping("/hr/resignations/{id}/decline")
    @Operation(summary = "Decline with a reason (HR)")
    public Resignation decline(@PathVariable Long id, @RequestBody Decision body) {
        return resignations.decline(id, body.note());
    }
}
