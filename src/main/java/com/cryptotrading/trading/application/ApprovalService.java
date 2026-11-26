package com.cryptotrading.trading.application;

import com.cryptotrading.audit.domain.AuditEvent;
import com.cryptotrading.security.application.SecondFactorVerifier;
import com.cryptotrading.shared.domain.BusinessRuleException;
import com.cryptotrading.trading.domain.ApprovalStore;
import com.cryptotrading.trading.domain.ProposalEvents;
import com.cryptotrading.trading.domain.ProposalRepository;
import com.cryptotrading.trading.domain.ProposalStatus;
import com.cryptotrading.trading.domain.TradeProposal;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Explicit user decision on a proposal. Approval requires: authenticated TRADER (enforced in the API),
 * a valid one-time TOTP code, the exact version + content hash that was displayed, and a non-expired proposal.
 */
@Service
public class ApprovalService {

    private final ProposalRepository proposals;
    private final ApprovalStore approvals;
    private final SecondFactorVerifier secondFactor;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    /** Failed attempts are audited in their own transaction so the rollback of the attempt does not erase them. */
    private final TransactionTemplate independent;

    public ApprovalService(ProposalRepository proposals, ApprovalStore approvals, SecondFactorVerifier secondFactor,
                           ApplicationEventPublisher events, Clock clock, PlatformTransactionManager transactionManager) {
        this.proposals = proposals;
        this.approvals = approvals;
        this.secondFactor = secondFactor;
        this.events = events;
        this.clock = clock;
        this.independent = new TransactionTemplate(transactionManager);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public TradeProposal approve(UUID proposalId, long version, String contentHash, String totpCode, String user, String clientIp) {
        try {
            return doApprove(proposalId, version, contentHash, totpCode, user, clientIp);
        } catch (BusinessRuleException e) {
            String action = "SECOND_FACTOR_INVALID".equals(e.code()) ? "APPROVAL_DENIED_2FA" : "APPROVAL_FAILED";
            independent.executeWithoutResult(s -> events.publishEvent(new AuditEvent(user, action, "proposal",
                    proposalId.toString(), Map.of("ip", String.valueOf(clientIp), "reason", e.code()))));
            throw e;
        }
    }

    private TradeProposal doApprove(UUID proposalId, long version, String contentHash, String totpCode, String user, String clientIp) {
        if (!secondFactor.verify(user, totpCode)) {
            throw new BusinessRuleException(BusinessRuleException.Kind.FORBIDDEN, "SECOND_FACTOR_INVALID",
                    "A valid, unused TOTP code is required (enroll TOTP first)");
        }
        TradeProposal proposal = load(proposalId);
        Instant now = clock.instant();
        proposal.approve(version, contentHash, now);
        proposals.save(proposal);
        approvals.record(proposalId, version, contentHash, "APPROVED", user, now, clientIp);
        events.publishEvent(new AuditEvent(user, "PROPOSAL_APPROVED", "proposal", proposalId.toString(), details(proposal, clientIp)));
        events.publishEvent(new ProposalEvents.StatusChanged(proposalId, ProposalStatus.APPROVED, null));
        events.publishEvent(new ProposalEvents.Approved(proposalId, user));
        return proposal;
    }

    @Transactional
    public TradeProposal reject(UUID proposalId, long version, String contentHash, String reason, String user, String clientIp) {
        TradeProposal proposal = load(proposalId);
        Instant now = clock.instant();
        String why = reason == null || reason.isBlank() ? "Rechazada por " + user : reason;
        proposal.reject(version, contentHash, why, now);
        proposals.save(proposal);
        approvals.record(proposalId, version, contentHash, "REJECTED", user, now, clientIp);
        events.publishEvent(new AuditEvent(user, "PROPOSAL_REJECTED", "proposal", proposalId.toString(), details(proposal, clientIp)));
        events.publishEvent(new ProposalEvents.StatusChanged(proposalId, ProposalStatus.REJECTED, why));
        return proposal;
    }

    private TradeProposal load(UUID id) {
        return proposals.find(id).orElseThrow(() -> new BusinessRuleException(BusinessRuleException.Kind.NOT_FOUND,
                "PROPOSAL_NOT_FOUND", "Proposal " + id + " not found"));
    }

    private static Map<String, Object> details(TradeProposal p, String ip) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("version", p.version());
        d.put("contentHash", p.contentHash());
        d.put("ip", String.valueOf(ip));
        return d;
    }
}
