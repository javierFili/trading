package com.cryptotrading.strategies.infrastructure;

import com.cryptotrading.configuration.TradingProperties;
import com.cryptotrading.strategies.domain.EmaRsiMacdStrategy;
import com.cryptotrading.strategies.domain.Strategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class StrategyConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "strategies.ema-rsi-macd", name = "enabled", havingValue = "true", matchIfMissing = true)
    Strategy emaRsiMacdStrategy(StrategyProperties properties, TradingProperties trading) {
        return new EmaRsiMacdStrategy(properties.toParameters(), trading.timeframe());
    }
}
