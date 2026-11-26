package com.cryptotrading.signals.presentation;

import com.cryptotrading.shared.domain.Symbol;
import com.cryptotrading.shared.domain.Timeframe;
import com.cryptotrading.signals.domain.Signal;
import com.cryptotrading.signals.domain.SignalStore;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
class SignalController {

    private final SignalStore store;
    private final Clock clock;

    SignalController(SignalStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @GetMapping("/signals")
    List<Signal> recent(@RequestParam(defaultValue = "50") int limit) {
        return store.recent(Math.min(Math.max(limit, 1), 500));
    }

    /** Indicator values recorded by the signal engine, for charting. */
    @GetMapping("/market/{symbol}/indicators")
    Map<String, Map<Instant, BigDecimal>> indicators(@PathVariable String symbol,
                                                     @RequestParam(defaultValue = "H1") Timeframe timeframe,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant end = to != null ? to : clock.instant();
        Instant start = from != null ? from : end.minus(timeframe.duration().multipliedBy(300));
        return store.indicators(Symbol.parse(symbol.replace('-', '/')), timeframe, start, end);
    }
}
