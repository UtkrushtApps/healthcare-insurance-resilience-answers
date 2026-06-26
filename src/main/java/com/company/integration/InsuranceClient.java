package com.company.integration;

import com.company.config.InsurerClientProperties;
import com.company.dto.InsurerEligibilityResponse;
import com.company.exception.ExternalEligibilityException;
import com.company.observability.CorrelationIdFilter;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

@Component
public class InsuranceClient {
    private static final Logger log = LoggerFactory.getLogger(InsuranceClient.class);

    private final WebClient insuranceWebClient;
    private final InsurerClientProperties properties;
    private final MeterRegistry meterRegistry;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public InsuranceClient(WebClient insuranceWebClient,
                           InsurerClientProperties properties,
                           MeterRegistry meterRegistry) {
        this.insuranceWebClient = insuranceWebClient;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.circuitBreaker = CircuitBreaker.of("insurerEligibility", CircuitBreakerConfig.custom()
                .slidingWindowSize(properties.circuitBreakerSlidingWindowSize())
                .minimumNumberOfCalls(properties.circuitBreakerMinimumNumberOfCalls())
                .failureRateThreshold(properties.circuitBreakerFailureRateThreshold())
                .waitDurationInOpenState(properties.circuitBreakerOpenStateDuration())
                .permittedNumberOfCallsInHalfOpenState(2)
                .recordException(ex -> !(ex instanceof NonRetryableEligibilityException))
                .ignoreExceptions(NonRetryableEligibilityException.class)
                .build());
        this.retry = Retry.of("insurerEligibility", RetryConfig.custom()
                .maxAttempts(properties.maxAttempts())
                .waitDuration(properties.retryBackoff())
                .retryOnException(retryableException())
                .build());
    }

    public Mono<InsurerEligibilityResponse> checkEligibility(String policyId) {
        String correlationId = MDC.get("correlationId");
        Timer.Sample sample = Timer.start(meterRegistry);

        return insuranceWebClient.get()
                .uri("/{policyId}/eligibility", policyId)
                .headers(headers -> addCorrelationHeader(headers, correlationId))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> new NonRetryableEligibilityException(response.statusCode().value(), body)))
                .onStatus(HttpStatusCode::is5xxServerError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> new TransientEligibilityException("Retryable insurer response status=" + response.statusCode().value() + " body=" + body)))
                .bodyToMono(InsurerEligibilityResponse.class)
                .timeout(properties.responseTimeout())
                .onErrorMap(this::mapException)
                .transformDeferred(RetryOperator.of(retry))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .doOnSuccess(response -> {
                    incrementOutcome("success");
                    log.info("event=insurer_eligibility_success policyId={} eligible={} decisionCode={}",
                            policyId, response != null && response.eligible(), response == null ? null : response.decisionCode());
                })
                .doOnError(ex -> {
                    incrementOutcome(classifyOutcome(ex));
                    log.warn("event=insurer_eligibility_failure policyId={} exceptionType={} message={}",
                            policyId, ex.getClass().getSimpleName(), ex.getMessage());
                })
                .doFinally(signal -> sample.stop(Timer.builder("insurer.eligibility.latency")
                        .description("Latency for insurer eligibility checks, including bounded retries")
                        .tag("signal", signal.name())
                        .register(meterRegistry)));
    }

    private void addCorrelationHeader(HttpHeaders headers, String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            headers.set(CorrelationIdFilter.HEADER, correlationId);
        }
    }

    private Predicate<Throwable> retryableException() {
        return ex -> Exceptions.unwrap(ex) instanceof TransientEligibilityException;
    }

    private Throwable mapException(Throwable throwable) {
        Throwable ex = Exceptions.unwrap(throwable);
        if (ex instanceof NonRetryableEligibilityException || ex instanceof TransientEligibilityException) {
            return ex;
        }
        if (ex instanceof TimeoutException || ex instanceof java.util.concurrent.TimeoutException) {
            return new TransientEligibilityException("Insurer response timed out after " + properties.responseTimeout(), ex);
        }
        if (ex instanceof WebClientRequestException) {
            return new TransientEligibilityException("Insurer request failed", ex);
        }
        if (ex instanceof WebClientResponseException responseException) {
            if (responseException.getStatusCode().is4xxClientError()) {
                return new NonRetryableEligibilityException(responseException.getStatusCode().value(), responseException.getResponseBodyAsString(), responseException);
            }
            return new TransientEligibilityException("Retryable insurer HTTP status " + responseException.getStatusCode().value(), responseException);
        }
        if (ex instanceof IOException) {
            return new TransientEligibilityException("Insurer I/O failure", ex);
        }
        return new TransientEligibilityException("Insurer eligibility check failed", ex);
    }

    private void incrementOutcome(String outcome) {
        Counter.builder("insurer.eligibility.outcomes")
                .description("Outcomes of insurer eligibility calls")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    private String classifyOutcome(Throwable throwable) {
        Throwable ex = Exceptions.unwrap(throwable);
        if (ex instanceof NonRetryableEligibilityException) {
            return "non_retryable";
        }
        if (ex instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException) {
            return "circuit_open";
        }
        if (ex instanceof TransientEligibilityException) {
            return "transient_failure";
        }
        return "failure";
    }

    public static class TransientEligibilityException extends ExternalEligibilityException {
        public TransientEligibilityException(String message) {
            super(message);
        }

        public TransientEligibilityException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class NonRetryableEligibilityException extends ExternalEligibilityException {
        private final int statusCode;

        public NonRetryableEligibilityException(int statusCode, String responseBody) {
            super("Non-retryable insurer response status=" + statusCode + " body=" + trim(responseBody));
            this.statusCode = statusCode;
        }

        public NonRetryableEligibilityException(int statusCode, String responseBody, Throwable cause) {
            super("Non-retryable insurer response status=" + statusCode + " body=" + trim(responseBody), cause);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }

        private static String trim(String value) {
            if (value == null) {
                return "";
            }
            return value.length() <= 300 ? value : value.substring(0, 300);
        }
    }
}
