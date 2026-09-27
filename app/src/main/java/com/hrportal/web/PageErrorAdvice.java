package com.hrportal.web;

import com.hrportal.service.BusinessRuleException;
import com.hrportal.service.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Friendly pages for errors that escape a browser controller. */
@ControllerAdvice(annotations = Controller.class)
public class PageErrorAdvice {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(NotFoundException e, Model model) {
        model.addAttribute("title", "Not found");
        model.addAttribute("message", e.getMessage());
        return "error/message";
    }

    @ExceptionHandler(BusinessRuleException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public String rule(BusinessRuleException e, Model model) {
        model.addAttribute("title", "That can't be done");
        model.addAttribute("message", e.getMessage());
        return "error/message";
    }
}
