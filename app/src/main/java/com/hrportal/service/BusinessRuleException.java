package com.hrportal.service;

/** A request broke an HR rule (e.g. not enough leave balance). Shown to the user as-is. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
