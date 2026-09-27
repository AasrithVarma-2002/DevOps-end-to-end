package com.hrportal.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** "Today" for leave rules is the company's local date, not the server's (containers run in UTC). */
    @Bean
    Clock clock(@Value("${app.time-zone:Asia/Kolkata}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
