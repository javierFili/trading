package com.cryptotrading.exchange.infrastructure.binance;

import com.cryptotrading.exchange.domain.ExchangeException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.cryptotrading.exchange.infrastructure.binance.BinanceTestSupport.BASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

class BinanceHttpTest {

    @Test
    void signedRequestCarriesApiKeyTimestampWithServerOffsetAndValidSignature() {
        BinanceTestSupport t = new BinanceTestSupport();
        t.expectTimeSync();
        AtomicReference<URI> captured = new AtomicReference<>();
        t.server.expect(requestTo(startsWith(BASE + "/api/v3/account?")))
                .andExpect(header("X-MBX-APIKEY", BinanceTestSupport.TEST_KEY))
                .andExpect(request -> captured.set(request.getURI()))
                .andRespond(withSuccess("{\"balances\":[]}", MediaType.APPLICATION_JSON));

        t.http.read("/api/v3/account", Map.of("omitZeroBalances", "true"), 20, true, BinanceDtos.Account.class);

        String query = captured.get().getRawQuery();
        assertThat(query).contains("recvWindow=5000").contains("timestamp=" + (BinanceTestSupport.NOW.toEpochMilli() + 250));
        String unsigned = query.substring(0, query.indexOf("&signature="));
        String signature = query.substring(query.indexOf("&signature=") + "&signature=".length());
        assertThat(signature).isEqualTo(new HmacSigner(BinanceTestSupport.TEST_SECRET).sign(unsigned));
        assertThat(query).doesNotContain(BinanceTestSupport.TEST_SECRET);
        t.server.verify();
    }

    @Test
    void hmacMatchesBinanceDocumentationExample() {
        // Example from the official Binance Spot API docs (SIGNED endpoint examples, HMAC).
        String secret = "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j";
        String payload = "symbol=LTCBTC&side=BUY&type=LIMIT&timeInForce=GTC&quantity=1&price=0.1&recvWindow=5000&timestamp=1499827319559";
        assertThat(new HmacSigner(secret).sign(payload))
                .isEqualTo("c8db56825ae71d6d79447849e617115f4a920fa2acdcab2b053c4b2838bd6b71");
    }

    @Test
    void readsAreRetriedOnServerErrors() {
        BinanceTestSupport t = new BinanceTestSupport();
        t.server.expect(requestTo(startsWith(BASE + "/api/v3/ticker/price"))).andRespond(withServerError());
        t.server.expect(requestTo(startsWith(BASE + "/api/v3/ticker/price")))
                .andRespond(withSuccess("{\"symbol\":\"BTCUSDT\",\"price\":\"64000.12000000\"}", MediaType.APPLICATION_JSON));

        BinanceDtos.TickerPrice price = t.http.read("/api/v3/ticker/price", Map.of("symbol", "BTCUSDT"), 2, false,
                BinanceDtos.TickerPrice.class);
        assertThat(price.price()).isEqualByComparingTo("64000.12");
        t.server.verify();
    }

    @Test
    void readsGiveUpAfterMaxAttempts() {
        BinanceTestSupport t = new BinanceTestSupport();
        for (int i = 0; i < 3; i++) {
            t.server.expect(requestTo(startsWith(BASE + "/api/v3/time"))).andRespond(withServerError());
        }
        assertThatThrownBy(() -> t.http.read("/api/v3/time", Map.of(), 1, false, BinanceHttp.ServerTime.class))
                .isInstanceOf(ExchangeException.Unavailable.class);
        t.server.verify();
    }

    @Test
    void http429BlocksFurtherRequestsLocally() {
        BinanceTestSupport t = new BinanceTestSupport();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "30");
        t.server.expect(once(), requestTo(startsWith(BASE + "/api/v3/time")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        assertThatThrownBy(() -> t.http.read("/api/v3/time", Map.of(), 1, false, BinanceHttp.ServerTime.class))
                .isInstanceOf(ExchangeException.RateLimited.class);
        // Second call must not hit the server at all.
        assertThatThrownBy(() -> t.http.read("/api/v3/time", Map.of(), 1, false, BinanceHttp.ServerTime.class))
                .isInstanceOf(ExchangeException.RateLimited.class)
                .hasMessageContaining("blocked");
        t.server.verify();
    }

    @Test
    void localWeightBudgetIsEnforcedAndSyncedFromHeaders() {
        BinanceTestSupport t = new BinanceTestSupport(10);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-USED-WEIGHT-1M", "9");
        t.server.expect(requestTo(startsWith(BASE + "/api/v3/time")))
                .andRespond(withSuccess("{\"serverTime\":1}", MediaType.APPLICATION_JSON).headers(headers));
        t.http.read("/api/v3/time", Map.of(), 1, false, BinanceHttp.ServerTime.class);
        assertThat(t.guard.usedWeight()).isEqualTo(9);
        assertThatThrownBy(() -> t.http.read("/api/v3/ticker/price", Map.of(), 2, false, BinanceDtos.TickerPrice.class))
                .isInstanceOf(ExchangeException.RateLimited.class);
    }

    @Test
    void timestampOutsideRecvWindowTriggersResyncAndOneRetry() {
        BinanceTestSupport t = new BinanceTestSupport();
        t.expectTimeSync();
        t.server.expect(requestTo(startsWith(BASE + "/api/v3/account")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":-1021,\"msg\":\"Timestamp for this request is outside of the recvWindow.\"}"));
        t.expectTimeSync();
        t.server.expect(requestTo(startsWith(BASE + "/api/v3/account")))
                .andRespond(withSuccess("{\"balances\":[]}", MediaType.APPLICATION_JSON));

        t.http.read("/api/v3/account", Map.of(), 20, true, BinanceDtos.Account.class);
        t.server.verify();
    }

    @Test
    void clientErrorsAreRejectedWithBinanceCode() {
        BinanceTestSupport t = new BinanceTestSupport();
        t.server.expect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":-1121,\"msg\":\"Invalid symbol.\"}"));
        assertThatThrownBy(() -> t.http.read("/api/v3/ticker/price", Map.of("symbol", "XXX"), 2, false,
                BinanceDtos.TickerPrice.class))
                .isInstanceOfSatisfying(ExchangeException.Rejected.class,
                        e -> assertThat(e.errorCode()).isEqualTo("-1121"));
    }
}
