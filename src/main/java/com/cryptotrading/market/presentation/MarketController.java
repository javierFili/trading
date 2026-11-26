package com.cryptotrading.market.presentation;

import com.cryptotrading.audit.domain.AuditEvent;
import com.cryptotrading.market.application.MarketDataService;
import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/market")
class MarketController {

    record CandleDto(Instant openTime, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume) {
    }

    record DownloadRequest(@NotNull Timeframe timeframe, @NotNull Instant from, @NotNull Instant to) {
    }

    private final MarketDataService market;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    MarketController(MarketDataService market, ApplicationEventPublisher events, TransactionTemplate tx, Clock clock) {
        this.market = market;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    /** {@code symbol} in the path uses a dash: {@code BTC-USDT}. */
    static Symbol symbol(String path) {
        return Symbol.parse(path.replace('-', '/'));
    }

    @GetMapping("/{symbol}/candles")
    List<CandleDto> candles(@PathVariable String symbol, @RequestParam(defaultValue = "H1") Timeframe timeframe,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant end = to != null ? to : clock.instant();
        Instant start = from != null ? from : end.minus(timeframe.duration().multipliedBy(300));
        if (Duration.between(start, end).compareTo(timeframe.duration().multipliedBy(5000)) > 0) {
            throw new IllegalArgumentException("At most 5000 candles per request");
        }
        return market.range(symbol(symbol), timeframe, start, end).stream()
                .map(c -> new CandleDto(c.openTime(), c.open(), c.high(), c.low(), c.close(), c.volume())).toList();
    }

    @PostMapping("/{symbol}/import")
    @PreAuthorize("hasRole('TRADER')")
    Map<String, Object> importCsv(@PathVariable String symbol, @RequestParam Timeframe timeframe,
                                  @RequestPart("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) throws IOException {
        int inserted;
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            inserted = market.importCsv(symbol(symbol), timeframe, reader);
        }
        audit(jwt, "CANDLES_IMPORTED", symbol, Map.of("timeframe", timeframe.name(), "inserted", inserted));
        return Map.of("inserted", inserted);
    }

    @PostMapping("/{symbol}/download")
    @PreAuthorize("hasRole('TRADER')")
    Map<String, Object> download(@PathVariable String symbol, @Valid @RequestBody DownloadRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        if (Duration.between(request.from(), request.to()).compareTo(Duration.ofDays(3 * 366)) > 0) {
            throw new IllegalArgumentException("Maximum download range is 3 years");
        }
        int inserted = market.downloadHistory(symbol(symbol), request.timeframe(), request.from(), request.to());
        audit(jwt, "CANDLES_DOWNLOADED", symbol, Map.of("timeframe", request.timeframe().name(), "inserted", inserted,
                "from", request.from().toString(), "to", request.to().toString()));
        return Map.of("inserted", inserted);
    }

    private void audit(Jwt jwt, String action, String symbol, Map<String, Object> details) {
        tx.executeWithoutResult(s -> events.publishEvent(new AuditEvent(jwt.getSubject(), action, "market", symbol, details)));
    }
}
