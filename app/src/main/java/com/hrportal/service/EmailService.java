package com.hrportal.service;

/** Sends plain-text email. Amazon SES in AWS; written to the log locally and in tests. */
public interface EmailService {

    void send(String to, String subject, String body);
}
