-- V1: baseline. Append-only audit log; the rest of the data model is introduced in Phase 1
-- (see docs/DATA_MODEL.md).

CREATE TABLE audit_log (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actor           VARCHAR(100) NOT NULL,             -- user name or 'system:<component>'
    action          VARCHAR(100) NOT NULL,             -- e.g. PROPOSAL_APPROVED, ORDER_SUBMITTED
    entity_type     VARCHAR(100),
    entity_id       VARCHAR(100),
    trading_mode    VARCHAR(10)  NOT NULL CHECK (trading_mode IN ('BACKTEST', 'TESTNET', 'LIVE')),
    correlation_id  UUID,
    details         JSONB        NOT NULL DEFAULT '{}'::jsonb   -- must never contain secrets
);

CREATE INDEX ix_audit_log_occurred_at ON audit_log (occurred_at);
CREATE INDEX ix_audit_log_entity ON audit_log (entity_type, entity_id);
CREATE INDEX ix_audit_log_correlation ON audit_log (correlation_id);

-- Append-only: updates and deletes are rejected at database level.
CREATE FUNCTION audit_log_reject_modification() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only (% not allowed)', TG_OP;
END;
$$;

CREATE TRIGGER audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_reject_modification();
