package com.zubairmuwwakil.marketdata.resilience;

import java.time.Duration;
import java.util.function.Supplier;

public class SimpleCircuitBreaker {

    private enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final Duration openStateDuration;
    private final int halfOpenMaxCalls;

    private State state = State.CLOSED;
    private int failureCount = 0;
    private int halfOpenAttempts = 0;
    private int halfOpenSuccesses = 0;
    private long openUntilMillis = 0L;

    public SimpleCircuitBreaker(int failureThreshold, Duration openStateDuration, int halfOpenMaxCalls) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openStateDuration = openStateDuration == null ? Duration.ofSeconds(30) : openStateDuration;
        this.halfOpenMaxCalls = Math.max(1, halfOpenMaxCalls);
    }

    public <T> T execute(Supplier<T> action) {
        enterIfAllowed();
        try {
            T result = action.get();
            onSuccess();
            return result;
        } catch (RuntimeException ex) {
            onFailure();
            throw ex;
        }
    }

    private synchronized void enterIfAllowed() {
        if (state == State.OPEN) {
            long now = System.currentTimeMillis();
            if (now < openUntilMillis) {
                throw new CircuitBreakerOpenException("Circuit breaker is open");
            }
            state = State.HALF_OPEN;
            halfOpenAttempts = 0;
            halfOpenSuccesses = 0;
        }
        if (state == State.HALF_OPEN) {
            if (halfOpenAttempts >= halfOpenMaxCalls) {
                throw new CircuitBreakerOpenException("Circuit breaker half-open trial limit reached");
            }
            halfOpenAttempts++;
        }
    }

    private synchronized void onSuccess() {
        if (state == State.HALF_OPEN) {
            halfOpenSuccesses++;
            if (halfOpenSuccesses >= halfOpenMaxCalls) {
                close();
            }
            return;
        }
        failureCount = 0;
    }

    private synchronized void onFailure() {
        if (state == State.HALF_OPEN) {
            open();
            return;
        }
        failureCount++;
        if (failureCount >= failureThreshold) {
            open();
        }
    }

    private void open() {
        state = State.OPEN;
        openUntilMillis = System.currentTimeMillis() + openStateDuration.toMillis();
    }

    private void close() {
        state = State.CLOSED;
        failureCount = 0;
        halfOpenAttempts = 0;
        halfOpenSuccesses = 0;
        openUntilMillis = 0L;
    }
}
