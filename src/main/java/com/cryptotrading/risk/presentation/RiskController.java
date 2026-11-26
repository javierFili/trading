package com.cryptotrading.risk.presentation;

import com.cryptotrading.configuration.ActiveAccount;
import com.cryptotrading.risk.application.CircuitBreakerService;
import com.cryptotrading.risk.application.RiskEventRecorder;
import com.cryptotrading.risk.application.RiskProperties;
import com.cryptotrading.security.application.SecondFactorVerifier;
import com.cryptotrading.shared.domain.BusinessRuleException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/risk")
class RiskController {

    record ResetRequest(@Pattern(regexp = "\\d{6}") String totpCode) {
    }

    private final RiskProperties risk;
    private final CircuitBreakerService breaker;
    private final RiskEventRecorder events;
    private final ActiveAccount account;
    private final SecondFactorVerifier secondFactor;

    RiskController(RiskProperties risk, CircuitBreakerService breaker, RiskEventRecorder events, ActiveAccount account,
                   SecondFactorVerifier secondFactor) {
        this.risk = risk;
        this.breaker = breaker;
        this.events = events;
        this.account = account;
        this.secondFactor = secondFactor;
    }

    @GetMapping("/status")
    Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("limits", risk);
        result.put("circuitBreaker", account.id().map(breaker::state).orElse(null));
        result.put("recentEvents", events.recent(50));
        return result;
    }

    @PostMapping("/circuit-breaker/reset")
    @PreAuthorize("hasRole('TRADER')")
    ResponseEntity<Void> reset(@Valid @RequestBody ResetRequest request, @AuthenticationPrincipal Jwt jwt) {
        if (!secondFactor.verify(jwt.getSubject(), request.totpCode())) {
            throw new BusinessRuleException(BusinessRuleException.Kind.FORBIDDEN, "SECOND_FACTOR_INVALID", "Valid TOTP code required");
        }
        breaker.close(account.requireId(), jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
