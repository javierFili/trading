package com.cryptotrading.audit.presentation;

import com.cryptotrading.audit.infrastructure.AuditLogWriter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/audit")
class AuditController {

    private final AuditLogWriter audit;

    AuditController(AuditLogWriter audit) {
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("hasRole('TRADER')")
    List<Map<String, Object>> recent(@RequestParam(defaultValue = "100") int limit) {
        return audit.recent(Math.min(Math.max(limit, 1), 1000));
    }
}
