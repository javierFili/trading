package com.cryptotrading.exchange.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.cryptotrading.shared.domain.CandleFixtures.BTC_USDT;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderRequestTest {

    private static final ClientOrderId ID = new ClientOrderId("cts-0001");

    @Test
    void validLimitAndMarketOrders() {
        assertThatCode(() -> new OrderRequest(ID, BTC_USDT, OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("0.001"), new BigDecimal("60000"), null, TimeInForce.GTC)).doesNotThrowAnyException();
        assertThatCode(() -> new OrderRequest(ID, BTC_USDT, OrderSide.SELL, OrderType.MARKET,
                new BigDecimal("0.001"), null, null, null)).doesNotThrowAnyException();
    }

    @Test
    void limitOrderRequiresPriceAndTimeInForce() {
        assertThatThrownBy(() -> new OrderRequest(ID, BTC_USDT, OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("0.001"), null, null, TimeInForce.GTC)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderRequest(ID, BTC_USDT, OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("0.001"), new BigDecimal("60000"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void marketOrderMustNotCarryPrice() {
        assertThatThrownBy(() -> new OrderRequest(ID, BTC_USDT, OrderSide.BUY, OrderType.MARKET,
                new BigDecimal("0.001"), new BigDecimal("60000"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void quantityMustBePositive() {
        assertThatThrownBy(() -> new OrderRequest(ID, BTC_USDT, OrderSide.BUY, OrderType.MARKET,
                BigDecimal.ZERO, null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clientOrderIdFollowsExchangeFormat() {
        assertThatThrownBy(() -> new ClientOrderId("has spaces")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClientOrderId("x".repeat(37))).isInstanceOf(IllegalArgumentException.class);
    }
}
