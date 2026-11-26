package com.cryptotrading.configuration;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
class SystemController {

    private final TradingProperties trading;
    private final Clock clock;

    SystemController(TradingProperties trading, Clock clock) {
        this.trading = trading;
        this.clock = clock;
    }

    @GetMapping("/status")
    Map<String, Object> status() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("mode", trading.mode());
        s.put("liveAvailable", TradingModeGuard.LIVE_SUPPORTED);
        s.put("symbols", trading.symbols());
        s.put("timeframe", trading.timeframe());
        s.put("approvalTtl", trading.approvalTtl().toString());
        s.put("maxPriceDeviation", trading.maxPriceDeviation());
        s.put("serverTime", clock.instant());
        return s;
    }
}
