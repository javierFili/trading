package com.cryptotrading.exchange.infrastructure.binance;

import com.cryptotrading.configuration.ConditionalOnExchangeMode;
import com.cryptotrading.configuration.PlatformConfiguration.TradingModeValidation;
import com.cryptotrading.exchange.domain.ExchangeClient;
import com.cryptotrading.exchange.domain.HistoricalCandleSource;
import com.cryptotrading.shared.domain.TradingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration(proxyBeanMethods = false)
public class BinanceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BinanceConfiguration.class);

    /** Market-data-only host: cannot trade, available in every mode (used to download backtest history). */
    @Bean
    HistoricalCandleSource historicalCandleSource(BinanceProperties props, JsonMapper json, Clock clock) {
        BinanceHttp http = http(BinanceEnvironment.PUBLIC_DATA, null, props, json, clock);
        return new BinanceMarketDataClient(http, clock);
    }

    @Bean
    @ConditionalOnExchangeMode
    @ConditionalOnProperty(prefix = "binance", name = "enabled", havingValue = "true", matchIfMissing = true)
    ExchangeClient binanceSpotClient(TradingModeValidation validation, BinanceProperties props, JsonMapper json,
                                     Clock clock) {
        TradingMode mode = validation.mode();
        BinanceEnvironment environment = BinanceEnvironment.forMode(mode);
        BinanceProperties.Credentials credentials = mode == TradingMode.LIVE ? props.live() : props.testnet();
        if (!credentials.isComplete()) {
            throw new IllegalStateException("Binance " + environment + " credentials missing: set CTS_BINANCE_"
                    + (mode == TradingMode.LIVE ? "LIVE" : "TESTNET") + "_API_KEY / _API_SECRET");
        }
        if (mode == TradingMode.LIVE && credentials.apiKey().equals(props.testnet().apiKey())) {
            throw new IllegalStateException("LIVE and TESTNET must use different API keys");
        }
        BinanceHttp http = http(environment, credentials, props, json, clock);
        BinanceAccountClient account = new BinanceAccountClient(http);
        if (mode == TradingMode.LIVE && account.withdrawalsEnabled()) {
            throw new IllegalStateException("Refusing LIVE: the API key has withdrawal permission enabled");
        }
        HttpClient wsClient = HttpClient.newBuilder().connectTimeout(props.connectTimeout()).build();
        // Not exposed as a bean: an Executor bean would disable Spring Boot's applicationTaskExecutor.
        ScheduledExecutorService streamScheduler =
                Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("binance-ws").factory());
        log.info("Binance adapter ready: {} ({})", environment, environment.restBaseUri());
        return new BinanceSpotClient(environment, new BinanceMarketDataClient(http, clock),
                new BinanceKlineStream(environment.streamBaseUri(), wsClient, json, streamScheduler),
                account, new BinanceOrderClient(http, json), streamScheduler);
    }

    static BinanceHttp http(BinanceEnvironment environment, BinanceProperties.Credentials credentials,
                            BinanceProperties props, JsonMapper json, Clock clock) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(props.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(props.readTimeout());
        RestClient rest = RestClient.builder().requestFactory(factory).build();
        return new BinanceHttp(rest, environment.restBaseUri(), json, credentials, clock, props.recvWindow(),
                new RateLimitGuard(clock, props.maxWeightPerMinute()),
                new Backoff(Duration.ofMillis(500), Duration.ofSeconds(10)),
                d -> Thread.sleep(d.toMillis()), props.maxReadAttempts());
    }
}
