package com.cryptotrading.exchange.infrastructure.binance;

import com.cryptotrading.exchange.domain.ExchangeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Low-level Binance REST transport: signing, timestamp/recvWindow, weight budget, error decoding and
 * retries. Reads are retried with backoff; state-changing requests ({@link #send}) are NEVER retried.
 * Secrets and signatures are never logged.
 */
final class BinanceHttp {

    private static final Logger log = LoggerFactory.getLogger(BinanceHttp.class);
    static final int ERR_TIMESTAMP_OUTSIDE_RECV_WINDOW = -1021;

    record RawResponse(int status, HttpHeaders headers, String body) {
        boolean isSuccess() {
            return status >= 200 && status < 300;
        }
    }

    record BinanceError(int code, String msg) {
    }

    /** The request may or may not have reached the exchange (network error / timeout). */
    static final class TransportException extends RuntimeException {
        TransportException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final RestClient rest;
    private final URI baseUri;
    private final JsonMapper json;
    private final String apiKey;
    private final HmacSigner signer;
    private final Clock clock;
    private final long recvWindowMs;
    private final RateLimitGuard guard;
    private final Backoff backoff;
    private final Sleeper sleeper;
    private final int maxReadAttempts;
    private volatile Long timeOffsetMs;

    BinanceHttp(RestClient rest, URI baseUri, JsonMapper json, BinanceProperties.Credentials credentials, Clock clock,
                Duration recvWindow, RateLimitGuard guard, Backoff backoff, Sleeper sleeper, int maxReadAttempts) {
        this.rest = rest;
        this.baseUri = baseUri;
        this.json = json;
        this.apiKey = credentials != null && credentials.isComplete() ? credentials.apiKey() : null;
        this.signer = credentials != null && credentials.isComplete() ? new HmacSigner(credentials.apiSecret()) : null;
        this.clock = clock;
        this.recvWindowMs = recvWindow.toMillis();
        this.guard = guard;
        this.backoff = backoff;
        this.sleeper = sleeper;
        this.maxReadAttempts = maxReadAttempts;
    }

    <T> T read(String path, Map<String, String> params, int weight, boolean signed, Class<T> type) {
        return json.readValue(readBody(path, params, weight, signed), type);
    }

    <T> T read(String path, Map<String, String> params, int weight, boolean signed, TypeReference<T> type) {
        return json.readValue(readBody(path, params, weight, signed), type);
    }

    /** Signed state-changing request. One attempt only; the caller interprets the raw outcome. */
    RawResponse send(HttpMethod method, String path, Map<String, String> params, int weight) {
        guard.acquire(weight);
        RawResponse response = execute(method, path, params, true);
        afterResponse(response);
        return response;
    }

    BinanceError parseError(String body) {
        try {
            return json.readValue(body, BinanceError.class);
        } catch (RuntimeException e) {
            return new BinanceError(0, "Unparseable error body");
        }
    }

    long timeOffsetMs() {
        Long offset = timeOffsetMs;
        return offset == null ? 0 : offset;
    }

    void synchronizeTime() {
        long before = clock.millis();
        ServerTime serverTime = json.readValue(readBody("/api/v3/time", Map.of(), 1, false), ServerTime.class);
        long after = clock.millis();
        long offset = serverTime.serverTime() - (before + after) / 2;
        timeOffsetMs = offset;
        if (Math.abs(offset) > recvWindowMs / 2) {
            log.warn("Local clock differs from Binance by {} ms (recvWindow {} ms)", offset, recvWindowMs);
        }
    }

    record ServerTime(long serverTime) {
    }

    private String readBody(String path, Map<String, String> params, int weight, boolean signed) {
        ExchangeException last = null;
        boolean timeResynced = false;
        for (int attempt = 0; attempt < maxReadAttempts; attempt++) {
            if (attempt > 0) {
                pause(backoff.delay(attempt));
            }
            guard.acquire(weight);
            RawResponse response;
            try {
                response = execute(HttpMethod.GET, path, params, signed);
            } catch (TransportException e) {
                last = new ExchangeException.Unavailable("Binance unreachable: " + e.getMessage(), e);
                continue;
            }
            afterResponse(response);
            if (response.isSuccess()) {
                return response.body();
            }
            int status = response.status();
            if (status == 429 || status == 418) {
                throw new ExchangeException.RateLimited("Binance rate limit (HTTP " + status + ")", retryAfter(response));
            }
            if (status >= 500) {
                last = new ExchangeException.Unavailable("Binance HTTP " + status, null);
                continue;
            }
            BinanceError error = parseError(response.body());
            if (error.code() == ERR_TIMESTAMP_OUTSIDE_RECV_WINDOW && signed && !timeResynced) {
                synchronizeTime();
                timeResynced = true;
                attempt--;
                continue;
            }
            throw new ExchangeException.Rejected(String.valueOf(error.code()), error.msg());
        }
        throw last;
    }

    private RawResponse execute(HttpMethod method, String path, Map<String, String> params, boolean signed) {
        String query;
        if (signed) {
            if (signer == null) {
                throw new IllegalStateException("Binance credentials are not configured for this environment");
            }
            if (timeOffsetMs == null) {
                synchronizeTime();
            }
            Map<String, String> all = new LinkedHashMap<>(params);
            all.put("recvWindow", String.valueOf(recvWindowMs));
            all.put("timestamp", String.valueOf(clock.millis() + timeOffsetMs()));
            String unsigned = encode(all);
            query = unsigned + "&signature=" + signer.sign(unsigned);
        } else {
            query = encode(params);
        }
        URI uri = URI.create(baseUri + path + (query.isEmpty() ? "" : "?" + query));
        try {
            return rest.method(method).uri(uri)
                    .headers(h -> {
                        if (signed) {
                            h.set("X-MBX-APIKEY", apiKey);
                        }
                    })
                    .exchange((request, response) -> new RawResponse(response.getStatusCode().value(),
                            response.getHeaders(), readFully(response.getBody().readAllBytes())));
        } catch (ResourceAccessException e) {
            throw new TransportException(method + " " + path + " failed: " + e.getMessage(), e);
        }
    }

    private static String readFully(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void afterResponse(RawResponse response) {
        String used = response.headers().getFirst("X-MBX-USED-WEIGHT-1M");
        if (used != null) {
            try {
                guard.observeUsedWeight(Integer.parseInt(used.trim()));
            } catch (NumberFormatException ignored) {
                // header is informative only
            }
        }
        if (response.status() == 429 || response.status() == 418) {
            guard.block(retryAfter(response), response.status() == 418);
            log.error("Binance rate limit response HTTP {}; requests blocked", response.status());
        }
    }

    private static Duration retryAfter(RawResponse response) {
        String value = response.headers().getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void pause(Duration duration) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeException.Unavailable("Interrupted while backing off", e);
        }
    }

    static String encode(Map<String, String> params) {
        StringJoiner joiner = new StringJoiner("&");
        params.forEach((k, v) -> joiner.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(v, StandardCharsets.UTF_8)));
        return joiner.toString();
    }
}
