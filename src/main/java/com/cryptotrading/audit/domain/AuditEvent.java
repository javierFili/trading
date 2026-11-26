package com.cryptotrading.audit.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Security- or money-relevant action, published as an application event and written to the append-only
 * {@code audit_log} in the same transaction. {@code details} must never contain secrets.
 *
 * @param actor user name or {@code system:<component>}
 */
public record AuditEvent(String actor, String action, String entityType, String entityId, Map<String, Object> details) {

    public AuditEvent {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static AuditEvent system(String component, String action, String entityType, Object entityId,
                                    Map<String, Object> details) {
        return new AuditEvent("system:" + component, action, entityType, entityId == null ? null : entityId.toString(), details);
    }
}
