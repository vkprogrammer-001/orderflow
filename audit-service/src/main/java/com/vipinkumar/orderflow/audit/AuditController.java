package com.vipinkumar.orderflow.audit;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditEventRepository repository;

    public AuditController(AuditEventRepository repository) {
        this.repository = repository;
    }

    /** Every command and reply for one order, in the order they happened. */
    @GetMapping("/orders/{orderId}")
    public List<AuditEvent> timeline(@PathVariable String orderId) {
        return repository.findByAggregateIdOrderByOccurredAtAsc(orderId);
    }

    @GetMapping("/events")
    public List<AuditEvent> recent(@RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return repository.findAllByOrderByOccurredAtDesc(PageRequest.of(0, limit));
    }
}
