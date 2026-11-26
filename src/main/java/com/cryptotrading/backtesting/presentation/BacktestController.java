package com.cryptotrading.backtesting.presentation;

import com.cryptotrading.backtesting.application.BacktestService;
import com.cryptotrading.backtesting.domain.BacktestRun;
import com.cryptotrading.backtesting.domain.BacktestStore;
import com.cryptotrading.backtesting.domain.BacktestTrade;
import com.cryptotrading.shared.domain.BusinessRuleException;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/backtests")
class BacktestController {

    record StartRequest(@NotBlank String symbol, @NotNull Timeframe timeframe, @NotNull Instant from, @NotNull Instant to,
                        @NotBlank String strategyCode, Map<String, String> parameters, Boolean split,
                        @DecimalMin("1") BigDecimal initialCapital) {
    }

    private final BacktestService service;
    private final BacktestStore store;

    BacktestController(BacktestService service, BacktestStore store) {
        this.service = service;
        this.store = store;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('TRADER')")
    Map<String, UUID> start(@Valid @RequestBody StartRequest r, @AuthenticationPrincipal Jwt jwt) {
        UUID group = service.start(new BacktestService.BacktestRequest(Symbol.parse(r.symbol()), r.timeframe(), r.from(), r.to(),
                r.strategyCode(), r.parameters() == null ? Map.of() : r.parameters(), Boolean.TRUE.equals(r.split()), r.initialCapital()), jwt.getSubject());
        return Map.of("groupId", group);
    }

    @GetMapping
    List<BacktestRun> recent(@RequestParam(defaultValue = "30") int limit) {
        return store.recent(Math.min(Math.max(limit, 1), 200));
    }

    @GetMapping("/groups/{groupId}")
    List<BacktestRun> group(@PathVariable UUID groupId) {
        return store.group(groupId);
    }

    @GetMapping("/{runId}")
    BacktestRun run(@PathVariable UUID runId) {
        return store.find(runId).orElseThrow(() ->
                new BusinessRuleException(BusinessRuleException.Kind.NOT_FOUND, "BACKTEST_NOT_FOUND", "Backtest not found"));
    }

    @GetMapping("/{runId}/trades")
    List<BacktestTrade> trades(@PathVariable UUID runId) {
        return store.trades(runId);
    }
}
