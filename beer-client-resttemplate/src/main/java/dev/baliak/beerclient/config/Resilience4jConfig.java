package dev.baliak.beerclient.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Central resilience configuration for outbound communication
 * with the Beer Store microservice.
 *
 * <p>This configuration defines and centralizes fault-tolerance mechanisms:
 *
 * <ul>
 *     <li><b>Circuit Breaker</b> – prevents cascading failures</li>
 *     <li><b>Retry</b> – automatically retries transient failures</li>
 *     <li><b>Rate Limiter</b> – protects downstream service from overload</li>
 * </ul>
 *
 * <p>These components work together to:
 * <ul>
 *     <li>Improve system stability</li>
 *     <li>Reduce cascading outages</li>
 *     <li>Prevent retry storms</li>
 *     <li>Control outbound traffic</li>
 * </ul>
 *
 * <p>Important:
 * Resilience mechanisms must always be carefully tuned
 * according to real production metrics.
 */
@Configuration
public class Resilience4jConfig {

    /**
     * Defines CircuitBreaker configuration.
     *
     * <p><b>Purpose:</b>
     * Prevents repeated calls to an unhealthy downstream service.
     *
     * <p><b>How it works:</b>
     * The circuit breaker monitors call results inside a sliding window.
     * If the failure rate exceeds the configured threshold,
     * the circuit transitions to OPEN state.
     *
     * <p><b>States:</b>
     * <ul>
     *     <li><b>CLOSED</b> – normal operation, calls allowed</li>
     *     <li><b>OPEN</b> – calls blocked (fail fast)</li>
     *     <li><b>HALF_OPEN</b> – limited test calls allowed</li>
     * </ul>
     *
     * <p><b>Configuration explained:</b>
     * - slidingWindowType(COUNT_BASED):
     *   Evaluates the last N calls instead of time-based window.
     *
     * - slidingWindowSize(10):
     *   Circuit evaluates the last 10 calls.
     *
     * - failureRateThreshold(50):
     *   If more than 50% of calls fail → circuit opens.
     *
     * - waitDurationInOpenState(10s):
     *   After opening, circuit waits 10 seconds before transitioning to HALF_OPEN.
     *
     * - permittedNumberOfCallsInHalfOpenState(3):
     *   Allows 3 test calls to determine if service recovered.
     *
     * <p>This prevents:
     * - Thread exhaustion
     * - Retry storms
     * - Cascading service failures
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10) // evaluate last 10 calls
                .failureRateThreshold(50) // open if >50% failures
                .waitDurationInOpenState(Duration.ofSeconds(10)) // time before half-open
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();

        return CircuitBreakerRegistry.of(config);
    }

    /**
     * Defines Retry configuration.
     *
     * <p><b>Purpose:</b>
     * Automatically retries transient failures.
     *
     * <p><b>When Retry should be used:</b>
     * <ul>
     *     <li>Temporary network glitches</li>
     *     <li>Short-lived timeouts</li>
     *     <li>Connection resets</li>
     * </ul>
     *
     * <p><b>Configuration explained:</b>
     * - maxAttempts(3):
     *   One initial attempt + 2 retries.
     *
     * - waitDuration(500ms):
     *   Fixed delay between attempts.
     *
     * - retryExceptions(...):
     *   Only retries technical exceptions (NOT business 4xx errors).
     *
     * <p>Important:
     * Retry must always be combined with CircuitBreaker
     * to avoid retry storms under heavy failure conditions.
     */
    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)  // initial call + 2 retries
                .waitDuration(Duration.ofMillis(500))
                .retryExceptions(
                        java.net.SocketTimeoutException.class,
                        java.net.ConnectException.class
                )
                .build();

        return RetryRegistry.of(config);
    }

    /**
     * Defines RateLimiter configuration.
     *
     * <p><b>Purpose:</b>
     * Controls the rate of outbound calls to the downstream service.
     *
     * <p>This protects:
     * <ul>
     *     <li>Downstream service from overload</li>
     *     <li>Our application from generating excessive traffic</li>
     * </ul>
     *
     * <p><b>Configuration explained:</b>
     * - limitForPeriod(10):
     *   Maximum 10 calls allowed per refresh period.
     *
     * - limitRefreshPeriod(1 second):
     *   Quota resets every second.
     *
     * - timeoutDuration(0):
     *   If limit exceeded → fail immediately (no waiting).
     *
     * <p>This ensures:
     * - Predictable outbound traffic
     * - No thread blocking due to rate limiting
     */
    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(10)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build();

        return RateLimiterRegistry.of(config);
    }
}
