package com.company.config;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.insurer")
public record InsurerClientProperties(
        @NotBlank String baseUrl,
        Duration connectTimeout,
        Duration responseTimeout,
        Duration overallTimeout,
        int maxAttempts,
        Duration retryBackoff,
        int circuitBreakerSlidingWindowSize,
        int circuitBreakerMinimumNumberOfCalls,
        float circuitBreakerFailureRateThreshold,
        Duration circuitBreakerOpenStateDuration
) {
    public InsurerClientProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofMillis(500);
        }
        if (responseTimeout == null) {
            responseTimeout = Duration.ofSeconds(1);
        }
        if (overallTimeout == null) {
            overallTimeout = Duration.ofMillis(2500);
        }
        if (maxAttempts <= 0) {
            maxAttempts = 2;
        }
        if (retryBackoff == null) {
            retryBackoff = Duration.ofMillis(100);
        }
        if (circuitBreakerSlidingWindowSize <= 0) {
            circuitBreakerSlidingWindowSize = 6;
        }
        if (circuitBreakerMinimumNumberOfCalls <= 0) {
            circuitBreakerMinimumNumberOfCalls = 3;
        }
        if (circuitBreakerFailureRateThreshold <= 0) {
            circuitBreakerFailureRateThreshold = 50.0f;
        }
        if (circuitBreakerOpenStateDuration == null) {
            circuitBreakerOpenStateDuration = Duration.ofSeconds(10);
        }
    }
}
