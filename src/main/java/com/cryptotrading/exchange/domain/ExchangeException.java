package com.cryptotrading.exchange.domain;

import java.time.Duration;
import java.util.Optional;

/**
 * Base class of exchange failures for <em>read</em> operations. State-changing commands return
 * {@link OrderCommandResult} instead of throwing, so ambiguous outcomes cannot be swallowed by a catch.
 */
public class ExchangeException extends RuntimeException {

    public ExchangeException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExchangeException(String message) {
        super(message);
    }

    /** Network failure, timeout or 5xx. Safe to retry with backoff for read operations. */
    public static class Unavailable extends ExchangeException {
        public Unavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Rate limit hit (HTTP 429) or IP ban (418). Callers must stop sending requests until retryAfter. */
    public static class RateLimited extends ExchangeException {
        private final Duration retryAfter;

        public RateLimited(String message, Duration retryAfter) {
            super(message);
            this.retryAfter = retryAfter;
        }

        public Optional<Duration> retryAfter() {
            return Optional.ofNullable(retryAfter);
        }
    }

    /** The exchange rejected the request (4xx with an error code). Not retryable as-is. */
    public static class Rejected extends ExchangeException {
        private final String errorCode;

        public Rejected(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public String errorCode() {
            return errorCode;
        }
    }
}
