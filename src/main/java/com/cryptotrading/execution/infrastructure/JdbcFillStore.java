package com.cryptotrading.execution.infrastructure;

import com.cryptotrading.exchange.domain.Fill;
import com.cryptotrading.execution.domain.FillStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
class JdbcFillStore implements FillStore {

    private final JdbcTemplate jdbc;

    JdbcFillStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveAll(UUID orderId, List<Fill> fills) {
        for (Fill f : fills) {
            jdbc.update("""
                    INSERT INTO trade_execution (order_id, exchange_trade_id, price, quantity, quote_quantity, commission,
                        commission_asset, executed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (order_id, exchange_trade_id) DO NOTHING""",
                    orderId, f.tradeId(), f.price(), f.quantity(), f.quoteQuantity(), f.commission(), f.commissionAsset(),
                    Timestamp.from(f.executedAt()));
        }
    }
}
