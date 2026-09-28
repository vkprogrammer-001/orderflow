package com.vipinkumar.orderflow.payment.app;

import com.vipinkumar.orderflow.events.EventCodec;
import com.vipinkumar.orderflow.events.EventTypes;
import com.vipinkumar.orderflow.events.Payloads;
import com.vipinkumar.orderflow.events.Topics;
import com.vipinkumar.orderflow.outbox.OutboxWriter;
import com.vipinkumar.orderflow.payment.domain.Payment;
import com.vipinkumar.orderflow.payment.domain.PaymentStatus;
import org.springframework.stereotype.Component;

@Component
public class PaymentEvents {

    private final OutboxWriter outbox;
    private final EventCodec codec;

    public PaymentEvents(OutboxWriter outbox, EventCodec codec) {
        this.outbox = outbox;
        this.codec = codec;
    }

    public void outcome(Payment payment, String correlationId) {
        String key = payment.getOrderId().toString();
        var event = payment.getStatus() == PaymentStatus.AUTHORIZED
                ? codec.wrap(EventTypes.PAYMENT_AUTHORIZED, key,
                    new Payloads.PaymentAuthorized(payment.getOrderId(), payment.getId(), payment.getProviderReference()), correlationId)
                : codec.wrap(EventTypes.PAYMENT_DECLINED, key,
                    new Payloads.PaymentDeclined(payment.getOrderId(), payment.getDeclineReason()), correlationId);
        outbox.append(Topics.PAYMENT_EVENTS, event);
    }
}
