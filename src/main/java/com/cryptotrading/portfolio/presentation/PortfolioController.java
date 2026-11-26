package com.cryptotrading.portfolio.presentation;

import com.cryptotrading.configuration.ActiveAccount;
import com.cryptotrading.exchange.domain.Balance;
import com.cryptotrading.exchange.domain.ExchangeException;
import com.cryptotrading.portfolio.application.PortfolioService;
import com.cryptotrading.portfolio.domain.TradingPosition;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portfolio")
class PortfolioController {

    record PositionDto(UUID id, String symbol, String status, BigDecimal entryQuantity, BigDecimal averageEntryPrice,
                       BigDecimal entryQuote, BigDecimal averageExitPrice, BigDecimal exitQuote, BigDecimal stopLoss,
                       BigDecimal takeProfit, BigDecimal fees, BigDecimal realizedPnl, String exitReason,
                       boolean protectedByExchange, Instant openedAt, Instant closedAt) {
        static PositionDto of(TradingPosition p) {
            return new PositionDto(p.id(), p.symbol().toString(), p.status().name(), p.entryQuantity(), p.averageEntryPrice(),
                    p.entryQuote(), p.averageExitPrice(), p.exitQuote(), p.stopLoss(), p.takeProfit(), p.feesQuote(),
                    p.realizedPnl(), p.exitReason(), p.protectedByExchange(), p.openedAt(), p.closedAt());
        }
    }

    record PortfolioDto(String mode, List<Balance> balances, String balancesError, List<PositionDto> positions,
                        BigDecimal lossToday) {
    }

    private final PortfolioService portfolio;
    private final ActiveAccount account;

    PortfolioController(PortfolioService portfolio, ActiveAccount account) {
        this.portfolio = portfolio;
        this.account = account;
    }

    @GetMapping
    PortfolioDto get() {
        if (account.id().isEmpty()) {
            return new PortfolioDto(account.mode().name(), List.of(), null, List.of(), BigDecimal.ZERO);
        }
        UUID id = account.requireId();
        List<Balance> balances = List.of();
        String error = null;
        try {
            balances = portfolio.balances();
        } catch (ExchangeException e) {
            error = "Saldos no disponibles: " + e.getMessage();
        }
        return new PortfolioDto(account.mode().name(), balances, error,
                portfolio.recent(id, 50).stream().map(PositionDto::of).toList(), portfolio.lossToday(id));
    }
}
