package com.cryptotrading.audit.infrastructure;

import com.cryptotrading.audit.domain.AuditEvent;
import com.cryptotrading.configuration.TradingProperties;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/** Writes audit events synchronously, inside the publisher's transaction (audit and action commit together). */
@Component
public class AuditLogWriter {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final TradingProperties properties;

    AuditLogWriter(JdbcTemplate jdbc, JsonMapper json, TradingProperties properties) {
        this.jdbc = jdbc;
        this.json = json;
        this.properties = properties;
    }

    @EventListener
    public void on(AuditEvent event) {
        jdbc.update("""
                INSERT INTO audit_log (actor, action, entity_type, entity_id, trading_mode, details)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)""",
                event.actor(), event.action(), event.entityType(), event.entityId(), properties.mode().name(),
                json.writeValueAsString(event.details()));
    }

    public List<Map<String, Object>> recent(int limit) {
        return jdbc.queryForList("""
                SELECT id, occurred_at, actor, action, entity_type, entity_id, trading_mode, details::text AS details
                FROM audit_log ORDER BY id DESC LIMIT ?""", limit);
    }
}
