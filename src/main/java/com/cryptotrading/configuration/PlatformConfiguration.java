package com.cryptotrading.configuration;

import com.cryptotrading.shared.domain.TradingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableAsync
public class PlatformConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PlatformConfiguration.class);

    /**
     * All time-dependent logic (approval expiry, candle closing, daily loss window) must use this clock,
     * never {@code Instant.now()}, so it can be controlled in tests and backtests. Always UTC.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Declared as a bean so validation runs during context startup, before exchange components. */
    @Bean
    TradingModeValidation tradingModeValidation(TradingProperties properties) {
        TradingModeGuard.validate(properties);
        log.info("Trading mode: {}", properties.mode());
        return new TradingModeValidation(properties.mode());
    }

    @Bean
    ActiveAccount activeAccount(TradingModeValidation validation) {
        return ActiveAccount.forMode(validation.mode());
    }

    /** Marker proving the mode was validated; other beans may depend on it to enforce ordering. */
    public record TradingModeValidation(TradingMode mode) {
    }
}
