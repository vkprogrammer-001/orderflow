package com.vipinkumar.orderflow.inventory.messaging;

import com.vipinkumar.orderflow.events.EventCodec;
import com.vipinkumar.orderflow.events.Topics;
import com.vipinkumar.orderflow.inventory.app.ReservationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryCommandListener {

    private final ReservationService reservations;
    private final EventCodec codec;

    public InventoryCommandListener(ReservationService reservations, EventCodec codec) {
        this.reservations = reservations;
        this.codec = codec;
    }

    @KafkaListener(topics = Topics.INVENTORY_COMMANDS, groupId = "inventory")
    public void onCommand(String message) {
        reservations.handle(codec.fromJson(message));
    }
}
