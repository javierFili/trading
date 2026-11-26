package com.cryptotrading.exchange.infrastructure.binance;

import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

/** Builds a BinanceHttp against a mock HTTP server. Test credentials are dummies, never real keys. */
final class BinanceTestSupport {

    static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
    static final String TEST_KEY = "test-api-key";
    static final String TEST_SECRET = "test-api-secret";
    static final URI BASE = URI.create("https://testnet.binance.vision");

    final MockRestServiceServer server;
    final BinanceHttp http;
    final RateLimitGuard guard;
    final JsonMapper json = JsonMapper.builder().build();
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    BinanceTestSupport() {
        this(3000);
    }

    BinanceTestSupport(int maxWeight) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        guard = new RateLimitGuard(clock, maxWeight);
        http = new BinanceHttp(builder.build(), BASE, json,
                new BinanceProperties.Credentials(TEST_KEY, TEST_SECRET), clock, Duration.ofMillis(5000), guard,
                new Backoff(Duration.ofMillis(1), Duration.ofMillis(2)), d -> { }, 3);
    }

    /** Expects the initial time synchronisation done before the first signed request. */
    void expectTimeSync() {
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(BASE + "/api/v3/time"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "{\"serverTime\":" + (NOW.toEpochMilli() + 250) + "}",
                        org.springframework.http.MediaType.APPLICATION_JSON));
    }
}
