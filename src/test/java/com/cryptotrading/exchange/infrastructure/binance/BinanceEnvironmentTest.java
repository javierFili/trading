package com.cryptotrading.exchange.infrastructure.binance;

import com.cryptotrading.shared.domain.TradingMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BinanceEnvironmentTest {

    @Test
    void testnetModeNeverResolvesToProductionHosts() {
        BinanceEnvironment env = BinanceEnvironment.forMode(TradingMode.TESTNET);
        assertThat(env.restBaseUri().getHost()).isEqualTo("testnet.binance.vision");
        assertThat(env.streamBaseUri().getHost()).isEqualTo("stream.testnet.binance.vision");
    }

    @Test
    void liveModeUsesProductionHosts() {
        BinanceEnvironment env = BinanceEnvironment.forMode(TradingMode.LIVE);
        assertThat(env.restBaseUri().getHost()).isEqualTo("api.binance.com");
        assertThat(env.streamBaseUri().getHost()).isEqualTo("stream.binance.com");
    }

    @Test
    void backtestHasNoExchangeEnvironment() {
        assertThatThrownBy(() -> BinanceEnvironment.forMode(TradingMode.BACKTEST))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
