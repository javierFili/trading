package com.cryptotrading.risk.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persists risk events (rejections, breaker changes, failures) in {@code risk_event}. */
@Service
public class RiskEventRecorder {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public RiskEventRecorder(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void record(UUID accountId, UUID proposalId, String rule, String severity, String detail) {
        String truncated = detail.length() > 1000 ? detail.substring(0, 1000) : detail;
        jdbc.update("INSERT INTO risk_event (account_id, proposal_id, rule, severity, detail, occurred_at) VALUES (?, ?, ?, ?, ?, ?)",
                accountId, proposalId, rule, severity, truncated, Timestamp.from(clock.instant()));
    }

    public List<Map<String, Object>> recent(int limit) {
        return jdbc.queryForList("SELECT id, account_id, proposal_id, rule, severity, detail, occurred_at "
                + "FROM risk_event ORDER BY occurred_at DESC, id DESC LIMIT ?", limit);
    }
}
