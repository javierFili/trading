package com.cryptotrading.exchange.infrastructure.binance;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Binance adapter settings ({@code binance.*}). Credentials come exclusively from environment variables.
 * Endpoints are NOT configurable (see {@link BinanceEnvironment}).
 *
 * @param enabled            allows tests to replace the adapter with a fake; never weakens safety
 * @param maxWeightPerMinute local ceiling, deliberately below Binance's published REQUEST_WEIGHT limit
 */
@Validated
@ConfigurationProperties(prefix = "binance")
public record BinanceProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue Credentials testnet,
        @DefaultValue Credentials live,
        @DefaultValue("5000ms") Duration recvWindow,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("10s") Duration readTimeout,
        @Min(1) @Max(6000) @DefaultValue("3000") int maxWeightPerMinute,
        @Min(1) @Max(5) @DefaultValue("3") int maxReadAttempts) {

    public record Credentials(String apiKey, String apiSecret) {

        public boolean isComplete() {
            return apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank();
        }

        @Override
        public String toString() {
            return "Credentials[apiKey=" + (apiKey == null ? "<unset>" : "<set>") + ", apiSecret=<redacted>]";
        }
    }
}
