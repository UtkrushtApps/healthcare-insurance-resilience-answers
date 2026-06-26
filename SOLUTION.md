# Solution Steps

1. Add explicit insurer resilience configuration: connect timeout, per-attempt response timeout, overall blocking budget, retry count/backoff, and circuit-breaker thresholds under `app.insurer`. Bind these values with `InsurerClientProperties` and configure Reactor Netty through `WebClientConfig` so socket/connect/response waits are bounded.

2. Keep the public appointment endpoint synchronous, but make the synchronous wait deterministic by blocking the insurer `Mono` only with a small overall timeout. Do not let an insurer delay hold the database transaction open.

3. Move persistence into a separate transactional method. First read the appointment and call the insurer outside a transaction; then re-load the appointment with a pessimistic write lock and persist exactly one final safe decision. This keeps database state consistent and avoids long-running transactions while the remote call is slow.

4. Implement `InsuranceClient` with WebClient error classification: 2xx responses become eligibility decisions, 4xx responses become non-retryable exceptions, and 5xx/network/timeouts become transient exceptions. Retry only transient failures and never retry non-retryable insurer responses.

5. Wrap the insurer call with a Resilience4j circuit breaker. Configure the breaker to record transient failures, ignore non-retryable 4xx failures, and fail fast when the insurer is unhealthy so Tomcat request threads are not consumed by repeated slow calls.

6. Map insurer outcomes safely in `AppointmentService`: confirm only on an explicit eligible response for the same policy id; decline explicit ineligible or non-retryable responses; mark `PENDING_ELIGIBILITY_CHECK` for timeouts, 5xx, network errors, circuit-open failures, null responses, and policy mismatches.

7. Return HTTP 202 Accepted for confirmation requests that end in `PENDING_ELIGIBILITY_CHECK`, and HTTP 200 OK for confirmed/declined/already-processed appointments. Keep the GET endpoint as HTTP 200.

8. Add structured logs with stable `event=...` keys, appointment id, policy id, status, and error type. Propagate `X-Correlation-ID` to the insurer call and expose it in response headers/log MDC for incident review.

9. Add Micrometer counters/timers for appointment confirmation outcomes and insurer eligibility outcomes/latency. Permit `/actuator/metrics/**` and `/actuator/prometheus` in security so support engineers can inspect metrics.

10. Expand tests with an in-memory H2 database seeded like the PostgreSQL simulator data. Verify slow insurer latency is bounded, unavailable insurer leaves the appointment pending, eligible policies confirm, ineligible policies decline, and non-retryable 4xx responses are not retried and never confirm the appointment.

