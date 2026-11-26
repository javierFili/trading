package com.cryptotrading.trading.presentation;

import com.cryptotrading.shared.domain.BusinessRuleException;
import com.cryptotrading.trading.application.ApprovalService;
import com.cryptotrading.trading.domain.ProposalRepository;
import com.cryptotrading.trading.domain.ProposalStatus;
import com.cryptotrading.trading.domain.TradeProposal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/proposals")
class ProposalController {

    record ProposalDto(UUID id, UUID signalId, String symbol, UUID positionId, String purpose, String side,
                       BigDecimal referencePrice, BigDecimal stopLoss, BigDecimal takeProfit, BigDecimal quantity,
                       BigDecimal notional, BigDecimal riskAmount, BigDecimal estimatedFees, BigDecimal rewardToRisk,
                       List<String> justification, String status, String statusReason, String contentHash,
                       long version, Instant expiresAt, String mode, Instant createdAt, Instant updatedAt) {
        static ProposalDto of(TradeProposal p) {
            return new ProposalDto(p.id(), p.signalId(), p.symbol().toString(), p.positionId(), p.purpose().name(),
                    p.side().name(), p.referencePrice(), p.stopLoss(), p.takeProfit(), p.quantity(), p.notional(),
                    p.riskAmount(), p.estimatedFees(), p.rewardToRisk(), p.justification(), p.status().name(),
                    p.statusReason(), p.contentHash(), p.version(), p.expiresAt(), p.mode().name(), p.createdAt(), p.updatedAt());
        }
    }

    record ApproveRequest(long version, @NotBlank @Size(min = 64, max = 64) String contentHash,
                          @NotBlank @Pattern(regexp = "\\d{6}") String totpCode) {
    }

    record RejectRequest(long version, @NotBlank @Size(min = 64, max = 64) String contentHash, @Size(max = 300) String reason) {
    }

    private final ProposalRepository proposals;
    private final ApprovalService approvals;

    ProposalController(ProposalRepository proposals, ApprovalService approvals) {
        this.proposals = proposals;
        this.approvals = approvals;
    }

    @GetMapping
    List<ProposalDto> list(@RequestParam(required = false) Set<ProposalStatus> status,
                           @RequestParam(defaultValue = "50") int limit) {
        Set<ProposalStatus> filter = status == null || status.isEmpty() ? EnumSet.allOf(ProposalStatus.class) : status;
        return proposals.findByStatus(filter, Math.min(Math.max(limit, 1), 200)).stream().map(ProposalDto::of).toList();
    }

    @GetMapping("/{id}")
    ProposalDto get(@PathVariable UUID id) {
        return proposals.find(id).map(ProposalDto::of).orElseThrow(() ->
                new BusinessRuleException(BusinessRuleException.Kind.NOT_FOUND, "PROPOSAL_NOT_FOUND", "Proposal not found"));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('TRADER')")
    ProposalDto approve(@PathVariable UUID id, @Valid @RequestBody ApproveRequest body, @AuthenticationPrincipal Jwt jwt,
                        HttpServletRequest http) {
        return ProposalDto.of(approvals.approve(id, body.version(), body.contentHash(), body.totpCode(), jwt.getSubject(),
                http.getRemoteAddr()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('TRADER')")
    ProposalDto reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest body, @AuthenticationPrincipal Jwt jwt,
                       HttpServletRequest http) {
        return ProposalDto.of(approvals.reject(id, body.version(), body.contentHash(), body.reason(), jwt.getSubject(),
                http.getRemoteAddr()));
    }
}
