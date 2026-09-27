package com.hrportal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Every test runs on Wednesday 4 March 2026, 10:00 IST, so date rules are deterministic. */
@TestConfiguration
public class TestClockConfig {

    @Bean
    @Primary
    Clock testClock() {
        return Clock.fixed(Instant.parse("2026-03-04T04:30:00Z"), ZoneId.of("Asia/Kolkata"));
    }
}
