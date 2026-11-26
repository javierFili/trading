package com.cryptotrading.exchange.infrastructure.binance;

import com.cryptotrading.exchange.domain.ClientOrderId;
import com.cryptotrading.exchange.domain.SymbolRules;
import com.cryptotrading.shared.domain.Candle;
import com.cryptotrading.shared.domain.Timeframe;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;

import static com.cryptotrading.exchange.infrastructure.binance.BinanceTestSupport.BASE;
import static com.cryptotrading.shared.domain.CandleFixtures.BTC_USDT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BinanceReadClientsTest {

    @Test
    void klinesAreParsedExactlyAndEndTimeIsInclusiveMinusOne() {
        BinanceTestSupport t = new BinanceTestSupport();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-01T02:00:00Z");
        t.server.expect(requestTo(allOf(startsWith(BASE + "/api/v3/klines?"), containsString("interval=1h"),
                        containsString("startTime=" + start.toEpochMilli()),
                        containsString("endTime=" + (end.toEpochMilli() - 1)))))
                .andRespond(withSuccess("""
                        [[1767225600000,"87000.10","87500.00","86900.00","87400.55","12.34567",1767229199999,
                          "1077000.12",1500,"6.1","533000.0","0"]]
                        """, MediaType.APPLICATION_JSON));

        List<Candle> candles = new BinanceMarketDataClient(t.http, t.clock).candles(BTC_USDT, Timeframe.H1, start, end, 1000);

        assertThat(candles).singleElement().satisfies(c -> {
            assertThat(c.openTime()).isEqualTo(start);
            assertThat(c.close()).isEqualByComparingTo("87400.55");
            assertThat(c.volume()).isEqualByComparingTo("12.34567");
            assertThat(c.tradeCount()).isEqualTo(1500);
        });
    }

    @Test
    void exchangeInfoFiltersAreMapped() {
        BinanceTestSupport t = new BinanceTestSupport();
        t.server.expect(requestTo(startsWith(BASE + "/api/v3/exchangeInfo"))).andRespond(withSuccess("""
                {"symbols":[{"symbol":"BTCUSDT","status":"TRADING","baseAsset":"BTC","quoteAsset":"USDT","filters":[
                  {"filterType":"PRICE_FILTER","minPrice":"0.01000000","maxPrice":"1000000.00000000","tickSize":"0.01000000"},
                  {"filterType":"LOT_SIZE","minQty":"0.00001000","maxQty":"9000.00000000","stepSize":"0.00001000"},
                  {"filterType":"MARKET_LOT_SIZE","minQty":"0.00000000","maxQty":"100.0","stepSize":"0.00000000"},
                  {"filterType":"NOTIONAL","minNotional":"5.00000000","applyMinToMarket":true,"maxNotional":"9000000.00000000","applyMaxToMarket":false,"avgPriceMins":5}
                ]}]}
                """, MediaType.APPLICATION_JSON));

        SymbolRules rules = new BinanceMarketDataClient(t.http, t.clock).symbolRules(BTC_USDT);

        assertThat(rules.tradingEnabled()).isTrue();
        assertThat(rules.tickSize()).isEqualByComparingTo("0.01");
        assertThat(rules.stepSize()).isEqualByComparingTo("0.00001");
        assertThat(rules.marketStepSize()).isEqualByComparingTo("0");
        assertThat(rules.minNotional()).isEqualByComparingTo("5");
    }

    @Test
    void unknownOrderIsEmptyOnlyWhenExchangeSaysSo() {
        BinanceTestSupport t = new BinanceTestSupport();
        t.expectTimeSync();
        t.server.expect(requestTo(allOf(startsWith(BASE + "/api/v3/order?"), containsString("origClientOrderId=cts-x"))))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":-2013,\"msg\":\"Order does not exist.\"}"));

        assertThat(new BinanceAccountClient(t.http).findOrder(BTC_USDT, new ClientOrderId("cts-x"))).isEmpty();
    }

    @Test
    void klineStreamDeliversOnlyClosedCandles() {
        BinanceTestSupport t = new BinanceTestSupport();
        String open = """
                {"e":"kline","E":1,"s":"BTCUSDT","k":{"t":1767225600000,"T":1767229199999,"s":"BTCUSDT","i":"1h",
                 "o":"1","c":"2","h":"3","l":"1","v":"10","n":5,"x":false,"q":"20"}}""";
        String closed = open.replace("\"x\":false", "\"x\":true");
        assertThat(BinanceKlineStream.parseClosedCandle(t.json, BTC_USDT, Timeframe.H1, open)).isEmpty();
        assertThat(BinanceKlineStream.parseClosedCandle(t.json, BTC_USDT, Timeframe.H1, closed)).hasValueSatisfying(
                c -> assertThat(c.high()).isEqualByComparingTo("3"));
    }

    @Test
    void backoffGrowsAndIsCapped() {
        Backoff backoff = new Backoff(java.time.Duration.ofMillis(100), java.time.Duration.ofMillis(1000));
        assertThat(backoff.delay(0).toMillis()).isBetween(50L, 100L);
        assertThat(backoff.delay(3).toMillis()).isBetween(400L, 800L);
        assertThat(backoff.delay(20).toMillis()).isBetween(500L, 1000L);
    }
}
