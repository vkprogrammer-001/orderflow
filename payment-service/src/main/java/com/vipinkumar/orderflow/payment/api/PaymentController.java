package com.vipinkumar.orderflow.payment.api;

import com.vipinkumar.orderflow.payment.app.PaymentService;
import com.vipinkumar.orderflow.payment.domain.Payment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<PaymentView> forOrder(@PathVariable UUID orderId) {
        return ResponseEntity.of(payments.forOrder(orderId).map(PaymentView::from));
    }

    /** Deliberately omits customer id: the API exposes only what the caller needs. */
    public record PaymentView(UUID id, UUID orderId, BigDecimal amount, String currency, String method,
                              String status, String providerReference, String declineReason, Instant createdAt) {
        static PaymentView from(Payment p) {
            return new PaymentView(p.getId(), p.getOrderId(), p.getAmount(), p.getCurrency(), p.getMethod(),
                    p.getStatus().name(), p.getProviderReference(), p.getDeclineReason(), p.getCreatedAt());
        }
    }
}
