package com.cryptotrading.trading.application;

import com.cryptotrading.market.application.SymbolRulesProvider;
import com.cryptotrading.portfolio.application.PortfolioService;
import com.cryptotrading.risk.application.CircuitBreakerService;
import com.cryptotrading.risk.application.RiskProperties;
import com.cryptotrading.risk.domain.RiskContext;
import com.cryptotrading.shared.domain.Symbol;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/** Builds a {@link RiskContext} from FRESH data (exchange balances, positions, breaker, filters). */
@Component
public class RiskContextFactory {

    private final PortfolioService portfolio;
    private final CircuitBreakerService breaker;
    private final SymbolRulesProvider rules;
    private final RiskProperties risk;
    private final Clock clock;

    public RiskContextFactory(PortfolioService portfolio, CircuitBreakerService breaker, SymbolRulesProvider rules,
                              RiskProperties risk, Clock clock) {
        this.portfolio = portfolio;
        this.breaker = breaker;
        this.rules = rules;
        this.risk = risk;
        this.clock = clock;
    }

    public RiskContext build(UUID accountId, Symbol symbol) {
        return new RiskContext(clock.instant(), portfolio.freeBalance(symbol.quoteAsset()),
                portfolio.openPositionCount(accountId), portfolio.lossToday(accountId), breaker.isOpen(accountId),
                rules.rules(symbol), risk.feeRate(), risk.slippageRate(), risk.stopLimitBuffer(), risk.maxSymbolRulesAge());
    }
}
