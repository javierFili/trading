package com.cryptotrading.configuration;

import com.cryptotrading.shared.domain.TradingMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradingModeGuardTest {

    static TradingProperties props(TradingMode mode, TradingProperties.Live live) {
        return new TradingProperties(mode, java.util.List.of("BTC/USDT"), com.cryptotrading.shared.domain.Timeframe.H1,
                java.time.Duration.ofMinutes(10), new java.math.BigDecimal("0.003"), live);
    }

    @ParameterizedTest
    @EnumSource(value = TradingMode.class, names = {"BACKTEST", "TESTNET"})
    void nonLiveModesAreAccepted(TradingMode mode) {
        var properties = props(mode, new TradingProperties.Live(false, null));
        assertThatCode(() -> TradingModeGuard.validate(properties)).doesNotThrowAnyException();
    }

    @Test
    void liveIsRejectedWithoutOptIn() {
        var properties = props(TradingMode.LIVE, new TradingProperties.Live(false, null));
        assertThatThrownBy(() -> TradingModeGuard.validate(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trading.live.enabled");
    }

    @Test
    void liveIsLockedInThisBuildEvenWithFullOptIn() {
        var properties = props(TradingMode.LIVE,
                new TradingProperties.Live(true, TradingModeGuard.LIVE_CONFIRMATION_PHRASE));
        assertThatThrownBy(() -> TradingModeGuard.validate(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not available in this build");
    }

    @Test
    void confirmationPhraseIsNotPrintedByToString() {
        var live = new TradingProperties.Live(true, TradingModeGuard.LIVE_CONFIRMATION_PHRASE);
        org.assertj.core.api.Assertions.assertThat(live.toString())
                .doesNotContain(TradingModeGuard.LIVE_CONFIRMATION_PHRASE);
    }
}
