package com.vipinkumar.orderflow.payment.app;

import com.vipinkumar.orderflow.events.EventCodec;
import com.vipinkumar.orderflow.events.EventEnvelope;
import com.vipinkumar.orderflow.events.EventTypes;
import com.vipinkumar.orderflow.events.Payloads;
import com.vipinkumar.orderflow.outbox.InboxGuard;
import com.vipinkumar.orderflow.payment.domain.Payment;
import com.vipinkumar.orderflow.payment.domain.PaymentRepository;
import com.vipinkumar.orderflow.payment.provider.AuthorizationResult;
import com.vipinkumar.orderflow.payment.provider.PaymentRequest;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/** Saga participant: authorises payment exactly once per order. */
@Service
public class PaymentService {

    static final String CONSUMER = "payment";
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final PaymentEvents events;
    private final InboxGuard inbox;
    private final EventCodec codec;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final MeterRegistry meters;

    public PaymentService(PaymentRepository payments, PaymentGateway gateway, PaymentEvents events, InboxGuard inbox,
                          EventCodec codec, TransactionTemplate tx, Clock clock, MeterRegistry meters) {
        this.payments = payments;
        this.gateway = gateway;
        this.events = events;
        this.inbox = inbox;
        this.codec = codec;
        this.tx = tx;
        this.clock = clock;
        this.meters = meters;
    }

    public void handle(EventEnvelope command) {
        if (!EventTypes.AUTHORIZE_PAYMENT.equals(command.type())) {
            return;
        }
        var cmd = codec.payload(command, Payloads.AuthorizePayment.class);

        // 1. Already decided for this order? Re-send the same answer; never charge twice.
        Optional<Payment> existing = payments.findByOrderId(cmd.orderId());
        if (existing.isPresent()) {
            tx.executeWithoutResult(s -> {
                if (inbox.firstDelivery(CONSUMER, command.eventId())) {
                    events.outcome(existing.get(), command.correlationId());
                }
            });
            return;
        }

        // 2. Call the provider OUTSIDE any DB transaction, so a slow gateway can't pin a pooled connection.
        AuthorizationResult result = gateway.authorize(new PaymentRequest(
                cmd.orderId().toString(), cmd.customerId(), cmd.amount(), cmd.currency(), cmd.method()));

        // 3. Record the outcome and emit the reply atomically.
        try {
            tx.executeWithoutResult(s -> {
                if (!inbox.firstDelivery(CONSUMER, command.eventId())) {
                    return;
                }
                Payment payment = payments.saveAndFlush(Payment.record(cmd.orderId(), cmd.customerId(), cmd.amount(),
                        cmd.currency(), cmd.method(), result, clock.instant()));
                events.outcome(payment, command.correlationId());
                meters.counter("payments.outcome", "status", payment.getStatus().name(), "method", cmd.method()).increment();
            });
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            // Another delivery of the same command won the unique(order_id) race and already replied.
            log.info("Payment for order {} already recorded by a concurrent delivery", cmd.orderId());
        }
    }

    public Optional<Payment> forOrder(UUID orderId) {
        return payments.findByOrderId(orderId);
    }
}
