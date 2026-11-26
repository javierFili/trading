package com.cryptotrading.trading.infrastructure;

import com.cryptotrading.trading.domain.ApprovalStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
class JdbcApprovalStore implements ApprovalStore {

    private final JdbcTemplate jdbc;

    JdbcApprovalStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(UUID proposalId, long proposalVersion, String contentHash, String decision, String decidedBy,
                       Instant decidedAt, String clientIp) {
        jdbc.update("""
                INSERT INTO trade_approval (id, proposal_id, proposal_version, proposal_content_hash, decision, decided_by,
                    decided_at, client_ip) VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), proposalId, proposalVersion, contentHash, decision, decidedBy, Timestamp.from(decidedAt), clientIp);
    }
}
