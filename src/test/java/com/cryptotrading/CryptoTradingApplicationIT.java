package com.cryptotrading;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Boots the full context (BACKTEST mode) against PostgreSQL and verifies migrations and DB invariants. */
class CryptoTradingApplicationIT extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void contextStartsAndAllMigrationsApply() {
        Integer applied = jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(2);
    }

    @Test
    void auditLogIsAppendOnly() {
        jdbc.update("insert into audit_log(actor, action, trading_mode) values ('system:test', 'TEST', 'BACKTEST')");
        assertThatThrownBy(() -> jdbc.update("update audit_log set action = 'TAMPERED'"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit_log"))
                .hasMessageContaining("append-only");
    }

    @Test
    void riskLimitsAreAuditedAtStartup() {
        Integer count = jdbc.queryForObject("select count(*) from audit_log where action = 'RISK_LIMITS_LOADED'", Integer.class);
        assertThat(count).isPositive();
    }

    @Test
    void onlyOneOpenPositionPerAccountAndPairIsPossible() {
        String insert = """
                insert into trading_position (id, account_id, pair_id, status, entry_qty, avg_entry_price, entry_quote,
                    stop_loss, take_profit, opened_at)
                values (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1, 'OPEN', 1, 1, 1, 1, 2, now())""";
        jdbc.update(insert);
        assertThatThrownBy(() -> jdbc.update(insert)).hasMessageContaining("ux_one_open_position");
        jdbc.update("delete from trading_position where account_id = '00000000-0000-0000-0000-000000000001' and entry_quote = 1");
    }

    @Test
    void duplicateClientOrderIdIsRejectedByTheDatabase() {
        String insert = """
                insert into trading_order (id, account_id, pair_id, role, client_order_id, side, type, quantity, status,
                    created_at, updated_at)
                values (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 1, 'ENTRY', 'cts-dup-test', 'BUY',
                    'MARKET', 1, 'PENDING_SUBMIT', now(), now())""";
        jdbc.update(insert);
        assertThatThrownBy(() -> jdbc.update(insert)).hasMessageContaining("duplicate key");
    }
}
