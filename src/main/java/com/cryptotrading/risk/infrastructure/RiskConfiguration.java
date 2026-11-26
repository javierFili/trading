package com.cryptotrading.risk.infrastructure;

import com.cryptotrading.audit.domain.AuditEvent;
import com.cryptotrading.risk.application.RiskProperties;
import com.cryptotrading.risk.domain.DefaultRiskManager;
import com.cryptotrading.risk.domain.RiskManager;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
class RiskConfiguration {

    @Bean
    RiskManager riskManager() {
        return new DefaultRiskManager();
    }

    @Bean
    RiskLimitsStartupAudit riskLimitsStartupAudit(RiskProperties properties, ApplicationEventPublisher events,
                                                  TransactionTemplate tx) {
        return new RiskLimitsStartupAudit(properties, events, tx);
    }

    /** Records the effective risk limits on every start, so any configuration change is traceable. */
    record RiskLimitsStartupAudit(RiskProperties properties, ApplicationEventPublisher events, TransactionTemplate tx) {
        @EventListener(ApplicationReadyEvent.class)
        public void audit() {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("maxRiskPerTrade", properties.maxRiskPerTrade().toPlainString());
            details.put("maxDailyLoss", properties.maxDailyLoss().toPlainString());
            details.put("maxOpenPositions", properties.maxOpenPositions());
            details.put("minQuoteReserve", properties.minQuoteReserve().toPlainString());
            details.put("maxPositionNotional", properties.maxPositionNotional().toPlainString());
            details.put("minRewardToRisk", properties.minRewardToRisk().toPlainString());
            details.put("feeRate", properties.feeRate().toPlainString());
            details.put("slippageRate", properties.slippageRate().toPlainString());
            details.put("stopLimitBuffer", properties.stopLimitBuffer().toPlainString());
            tx.executeWithoutResult(s -> events.publishEvent(
                    AuditEvent.system("risk", "RISK_LIMITS_LOADED", "configuration", "risk", details)));
        }
    }
}
