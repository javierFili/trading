package com.cryptotrading.exchange.infrastructure.binance;

import com.cryptotrading.exchange.domain.ClientOrderId;
import com.cryptotrading.exchange.domain.ExchangeOrderStatus;
import com.cryptotrading.exchange.domain.OcoRequest;
import com.cryptotrading.exchange.domain.OrderCommandResult;
import com.cryptotrading.exchange.domain.OrderListCommandResult;
import com.cryptotrading.exchange.domain.OrderRequest;
import com.cryptotrading.exchange.domain.OrderSide;
import com.cryptotrading.exchange.domain.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.math.BigDecimal;

import static com.cryptotrading.exchange.infrastructure.binance.BinanceTestSupport.BASE;
import static com.cryptotrading.shared.domain.CandleFixtures.BTC_USDT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BinanceOrderClientTest {

    private static final ClientOrderId ID = new ClientOrderId("cts-entry-1");
    private static final OrderRequest MARKET_BUY = new OrderRequest(ID, BTC_USDT, OrderSide.BUY, OrderType.MARKET,
            new BigDecimal("0.00078"), null, null, null);

    private BinanceTestSupport t;
    private BinanceOrderClient client;

    @BeforeEach
    void setUp() {
        t = new BinanceTestSupport();
        client = new BinanceOrderClient(t.http, t.json);
        t.expectTimeSync();
    }

    @Test
    void acceptedOrderIsMappedAndSentOnce() {
        t.server.expect(once(), requestTo(allOf(startsWith(BASE + "/api/v3/order?"),
                        containsString("newClientOrderId=cts-entry-1"), containsString("quantity=0.00078"),
                        containsString("type=MARKET"))))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"symbol":"BTCUSDT","orderId":28,"orderListId":-1,"clientOrderId":"cts-entry-1",
                         "transactTime":1507725176595,"price":"0.00000000","origQty":"0.00078000",
                         "executedQty":"0.00078000","cummulativeQuoteQty":"49.92000000","status":"FILLED",
                         "timeInForce":"GTC","type":"MARKET","side":"BUY",
                         "fills":[{"price":"64000.00","qty":"0.00078000","commission":"0.00000078",
                                   "commissionAsset":"BTC","tradeId":56}]}
                        """, MediaType.APPLICATION_JSON));

        OrderCommandResult result = client.submit(MARKET_BUY);

        assertThat(result).isInstanceOfSatisfying(OrderCommandResult.Accepted.class, a -> {
            assertThat(a.order().status()).isEqualTo(ExchangeOrderStatus.FILLED);
            assertThat(a.order().executedQuantity()).isEqualByComparingTo("0.00078");
            assertThat(a.order().cumulativeQuoteQuantity()).isEqualByComparingTo("49.92");
            assertThat(a.order().exchangeOrderId()).isEqualTo("28");
        });
        t.server.verify();
    }

    @Test
    void serverErrorIsUnknownNotFailureAndIsNotRetried() {
        t.server.expect(once(), requestTo(startsWith(BASE + "/api/v3/order"))).andRespond(withServerError());
        assertThat(client.submit(MARKET_BUY)).isInstanceOf(OrderCommandResult.Unknown.class);
        t.server.verify();
    }

    @Test
    void backendTimeoutCodeIsUnknown() {
        t.server.expect(once(), requestTo(startsWith(BASE + "/api/v3/order")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":-1007,\"msg\":\"Timeout waiting for response from backend server.\"}"));
        assertThat(client.submit(MARKET_BUY)).isInstanceOf(OrderCommandResult.Unknown.class);
    }

    @Test
    void networkFailureIsUnknown() {
        t.server.expect(once(), requestTo(startsWith(BASE + "/api/v3/order")))
                .andRespond(withException(new IOException("read timed out")));
        assertThat(client.submit(MARKET_BUY)).isInstanceOfSatisfying(OrderCommandResult.Unknown.class,
                u -> assertThat(u.clientOrderId()).isEqualTo(ID));
        t.server.verify();
    }

    @Test
    void businessErrorIsRejected() {
        t.server.expect(once(), requestTo(startsWith(BASE + "/api/v3/order")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":-2010,\"msg\":\"Account has insufficient balance for requested action.\"}"));
        assertThat(client.submit(MARKET_BUY)).isInstanceOfSatisfying(OrderCommandResult.Rejected.class,
                r -> assertThat(r.errorCode()).isEqualTo("-2010"));
    }

    @Test
    void duplicateClientOrderIdIsRejectedByExchangeNotResent() {
        t.server.expect(once(), requestTo(startsWith(BASE + "/api/v3/order")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":-2010,\"msg\":\"Duplicate order sent.\"}"));
        assertThat(client.submit(MARKET_BUY)).isInstanceOf(OrderCommandResult.Rejected.class);
        t.server.verify();
    }

    @Test
    void ocoUsesAboveLimitMakerAndBelowStopLossLimit() {
        t.server.expect(once(), requestTo(allOf(startsWith(BASE + "/api/v3/orderList/oco?"),
                        containsString("side=SELL"), containsString("aboveType=LIMIT_MAKER"),
                        containsString("abovePrice=66000"), containsString("belowType=STOP_LOSS_LIMIT"),
                        containsString("belowStopPrice=63000"), containsString("belowPrice=62900"),
                        containsString("belowTimeInForce=GTC"))))
                .andRespond(withSuccess("""
                        {"orderListId":1,"listClientOrderId":"cts-oco-1","listOrderStatus":"EXECUTING",
                         "orderReports":[
                          {"symbol":"BTCUSDT","orderId":2,"clientOrderId":"cts-sl-1","price":"62900","origQty":"0.00077",
                           "executedQty":"0","cummulativeQuoteQty":"0","status":"NEW","type":"STOP_LOSS_LIMIT","side":"SELL","transactTime":1},
                          {"symbol":"BTCUSDT","orderId":3,"clientOrderId":"cts-tp-1","price":"66000","origQty":"0.00077",
                           "executedQty":"0","cummulativeQuoteQty":"0","status":"NEW","type":"LIMIT_MAKER","side":"SELL","transactTime":1}]}
                        """, MediaType.APPLICATION_JSON));

        OrderListCommandResult result = client.submitOco(new OcoRequest(new ClientOrderId("cts-oco-1"),
                new ClientOrderId("cts-tp-1"), new ClientOrderId("cts-sl-1"), BTC_USDT, new BigDecimal("0.00077"),
                new BigDecimal("66000"), new BigDecimal("63000"), new BigDecimal("62900")));

        assertThat(result).isInstanceOfSatisfying(OrderListCommandResult.Accepted.class,
                a -> assertThat(a.orders()).hasSize(2));
        t.server.verify();
    }
}
