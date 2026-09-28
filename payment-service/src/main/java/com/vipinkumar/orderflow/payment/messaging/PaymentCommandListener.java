package com.vipinkumar.orderflow.payment.messaging;

import com.vipinkumar.orderflow.events.EventCodec;
import com.vipinkumar.orderflow.events.Topics;
import com.vipinkumar.orderflow.payment.app.PaymentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentCommandListener {

    private final PaymentService payments;
    private final EventCodec codec;

    public PaymentCommandListener(PaymentService payments, EventCodec codec) {
        this.payments = payments;
        this.codec = codec;
    }

    @KafkaListener(topics = Topics.PAYMENT_COMMANDS, groupId = "payment")
    public void onCommand(String message) {
        payments.handle(codec.fromJson(message));
    }
}
