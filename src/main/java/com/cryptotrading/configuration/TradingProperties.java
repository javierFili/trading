package com.cryptotrading.configuration;

import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;
import com.cryptotrading.shared.domain.TradingMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Top-level platform configuration ({@code trading.*}).
 *
 * @param mode              operating mode; defaults to {@link TradingMode#BACKTEST}
 * @param symbols           pairs analysed, e.g. {@code BTC/USDT}
 * @param timeframe         analysis timeframe
 * @param approvalTtl       how long a proposal (and its approval) stays valid
 * @param maxPriceDeviation max relative price move between proposal and execution (0.003 = 0.3 %)
 * @param live              LIVE unlock settings; ignored unless {@code mode=LIVE}
 */
@Validated
@ConfigurationProperties(prefix = "trading")
public record TradingProperties(
        @NotNull @DefaultValue("BACKTEST") TradingMode mode,
        @NotEmpty @DefaultValue("BTC/USDT") List<String> symbols,
        @NotNull @DefaultValue("H1") Timeframe timeframe,
        @NotNull @DefaultValue("10m") Duration approvalTtl,
        @NotNull @DecimalMin("0.0001") @DecimalMax("0.05") @DefaultValue("0.003") BigDecimal maxPriceDeviation,
        @Valid @NotNull @DefaultValue Live live) {

    public List<Symbol> parsedSymbols() {
        return symbols.stream().map(Symbol::parse).toList();
    }

    /**
     * @param enabled      explicit opt-in flag for LIVE
     * @param confirmation must equal {@link TradingModeGuard#LIVE_CONFIRMATION_PHRASE}
     */
    public record Live(@DefaultValue("false") boolean enabled, String confirmation) {

        @Override
        public String toString() {
            return "Live[enabled=" + enabled + ", confirmation=" + (confirmation == null ? "<unset>" : "<set>") + "]";
        }
    }
}
