package com.cryptotrading.execution.presentation;

import com.cryptotrading.execution.domain.OrderRecord;
import com.cryptotrading.execution.domain.OrderRecordRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
class OrderController {

    record OrderDto(UUID id, UUID proposalId, UUID positionId, String symbol, String role, String clientOrderId,
                    String exchangeOrderId, String side, String type, BigDecimal quantity, BigDecimal price,
                    BigDecimal stopPrice, String status, BigDecimal executedQuantity, BigDecimal cumulativeQuoteQuantity,
                    String statusReason, Instant createdAt, Instant updatedAt, Instant lastReconciledAt) {
        static OrderDto of(OrderRecord o) {
            return new OrderDto(o.id(), o.proposalId(), o.positionId(), o.symbol().toString(), o.role().name(),
                    o.clientOrderId().value(), o.exchangeOrderId(), o.side().name(), o.type().name(), o.quantity(), o.price(),
                    o.stopPrice(), o.status().name(), o.executedQuantity(), o.cumulativeQuoteQuantity(), o.statusReason(),
                    o.createdAt(), o.updatedAt(), o.lastReconciledAt());
        }
    }

    private final OrderRecordRepository orders;

    OrderController(OrderRecordRepository orders) {
        this.orders = orders;
    }

    @GetMapping
    List<OrderDto> recent(@RequestParam(defaultValue = "100") int limit) {
        return orders.recent(Math.min(Math.max(limit, 1), 500)).stream().map(OrderDto::of).toList();
    }
}
