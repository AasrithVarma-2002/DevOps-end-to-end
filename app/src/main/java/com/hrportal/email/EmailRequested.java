package com.hrportal.email;

/** Published when a notification should also go out by email; sent after the transaction commits. */
public record EmailRequested(String to, String subject, String body) {
}
