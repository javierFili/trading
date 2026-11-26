package com.cryptotrading.portfolio.infrastructure;

import com.cryptotrading.configuration.ActiveAccount;
import com.cryptotrading.configuration.ConditionalOnExchangeMode;
import com.cryptotrading.configuration.TradingProperties;
import com.cryptotrading.exchange.domain.Balance;
import com.cryptotrading.exchange.domain.ExchangeClient;
import com.cryptotrading.portfolio.application.PortfolioService;
import com.cryptotrading.shared.domain.Symbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;

/** Hourly valuation of the account in the quote asset of the first configured symbol. */
@Component
@ConditionalOnExchangeMode
class PortfolioSnapshotJob {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSnapshotJob.class);

    private final PortfolioService portfolio;
    private final ExchangeClient exchange;
    private final TradingProperties trading;
    private final ActiveAccount account;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final Clock clock;

    PortfolioSnapshotJob(PortfolioService portfolio, ExchangeClient exchange, TradingProperties trading,
                         ActiveAccount account, JdbcTemplate jdbc, JsonMapper json, Clock clock) {
        this.portfolio = portfolio;
        this.exchange = exchange;
        this.trading = trading;
        this.account = account;
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Scheduled(cron = "${portfolio.snapshot-cron:0 5 * * * *}")
    void snapshot() {
        try {
            List<Balance> balances = portfolio.balances();
            String quote = trading.parsedSymbols().getFirst().quoteAsset();
            BigDecimal total = BigDecimal.ZERO;
            for (Balance b : balances) {
                if (b.asset().equals(quote)) {
                    total = total.add(b.total());
                } else {
                    for (Symbol s : trading.parsedSymbols()) {
                        if (s.baseAsset().equals(b.asset()) && b.total().signum() > 0) {
                            total = total.add(b.total().multiply(exchange.marketData().latestPrice(s)));
                        }
                    }
                }
            }
            jdbc.update("INSERT INTO portfolio_snapshot (account_id, taken_at, balances, total_value_quote, realized_pnl_day) "
                            + "VALUES (?, ?, ?::jsonb, ?, ?)", account.requireId(), Timestamp.from(clock.instant()),
                    json.writeValueAsString(balances), total, portfolio.lossToday(account.requireId()).negate());
        } catch (RuntimeException e) {
            log.warn("Portfolio snapshot failed: {}", e.getMessage());
        }
    }
}
