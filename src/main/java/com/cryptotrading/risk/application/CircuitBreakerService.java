package com.cryptotrading.risk.application;

import com.cryptotrading.audit.domain.AuditEvent;
import com.cryptotrading.risk.domain.RiskAlertEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent circuit breaker per account. When open, no proposal can be created or executed. It opens
 * automatically (consecutive execution failures, daily loss limit, unprotected position) and closes ONLY
 * through an authenticated, audited manual action.
 */
@Service
public class CircuitBreakerService {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerService.class);

    public record State(boolean open, String reason, int consecutiveFailures, Instant changedAt, String changedBy) {
    }

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final RiskProperties properties;
    private final RiskEventRecorder recorder;
    private final Clock clock;

    public CircuitBreakerService(JdbcTemplate jdbc, ApplicationEventPublisher events, RiskProperties properties,
                                 RiskEventRecorder recorder, Clock clock) {
        this.jdbc = jdbc;
        this.events = events;
        this.properties = properties;
        this.recorder = recorder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public State state(UUID accountId) {
        return jdbc.queryForObject("SELECT is_open, reason, consecutive_failures, changed_at, changed_by "
                        + "FROM circuit_breaker WHERE account_id = ?",
                (rs, i) -> new State(rs.getBoolean(1), rs.getString(2), rs.getInt(3), rs.getTimestamp(4).toInstant(),
                        rs.getString(5)), accountId);
    }

    public boolean isOpen(UUID accountId) {
        return state(accountId).open();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void open(UUID accountId, String reason, String actor) {
        int changed = jdbc.update("UPDATE circuit_breaker SET is_open = TRUE, reason = ?, changed_at = ?, changed_by = ?, "
                + "version = version + 1 WHERE account_id = ? AND is_open = FALSE", reason, now(), actor, accountId);
        if (changed == 1) {
            log.error("CIRCUIT BREAKER OPEN for {}: {}", accountId, reason);
            recorder.record(accountId, null, "CIRCUIT_BREAKER_OPEN", "CRITICAL", reason);
            events.publishEvent(new AuditEvent(actor, "CIRCUIT_BREAKER_OPENED", "account", accountId.toString(),
                    Map.of("reason", reason)));
            events.publishEvent(new RiskAlertEvent(accountId, "CIRCUIT_BREAKER_OPEN", "CRITICAL", reason));
        }
    }

    @Transactional
    public void close(UUID accountId, String actor) {
        jdbc.update("UPDATE circuit_breaker SET is_open = FALSE, reason = NULL, consecutive_failures = 0, changed_at = ?, "
                + "changed_by = ?, version = version + 1 WHERE account_id = ?", now(), actor, accountId);
        recorder.record(accountId, null, "CIRCUIT_BREAKER_CLOSED", "WARNING", "Cerrado manualmente por " + actor);
        events.publishEvent(new AuditEvent(actor, "CIRCUIT_BREAKER_CLOSED", "account", accountId.toString(), Map.of()));
        events.publishEvent(new RiskAlertEvent(accountId, "CIRCUIT_BREAKER_CLOSED", "WARNING", "Cerrado por " + actor));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID accountId, String reason) {
        jdbc.update("UPDATE circuit_breaker SET consecutive_failures = consecutive_failures + 1 WHERE account_id = ?", accountId);
        recorder.record(accountId, null, "EXECUTION_FAILURE", "WARNING", reason);
        int failures = state(accountId).consecutiveFailures();
        if (failures >= properties.maxConsecutiveFailures()) {
            open(accountId, failures + " fallos de ejecución consecutivos; último: " + reason, "system:risk");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(UUID accountId) {
        jdbc.update("UPDATE circuit_breaker SET consecutive_failures = 0 WHERE account_id = ? AND consecutive_failures > 0",
                accountId);
    }

    private Timestamp now() {
        return Timestamp.from(clock.instant());
    }
}
