package com.vipinkumar.orderflow.inventory.app;

import com.vipinkumar.orderflow.events.EventCodec;
import com.vipinkumar.orderflow.events.EventTypes;
import com.vipinkumar.orderflow.events.Payloads;
import com.vipinkumar.orderflow.events.Topics;
import com.vipinkumar.orderflow.inventory.domain.Reservation;
import com.vipinkumar.orderflow.outbox.OutboxWriter;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InventoryEvents {

    private final OutboxWriter outbox;
    private final EventCodec codec;

    public InventoryEvents(OutboxWriter outbox, EventCodec codec) {
        this.outbox = outbox;
        this.codec = codec;
    }

    public void reserved(Reservation r, String currency, String correlationId) {
        var lines = r.getLines().stream()
                .map(l -> new Payloads.PricedLine(l.getSku(), l.getQuantity(), l.getUnitPrice())).toList();
        emit(EventTypes.INVENTORY_RESERVED, r.getOrderId(),
                new Payloads.InventoryReserved(r.getOrderId(), lines, currency), correlationId);
    }

    public void rejected(UUID orderId, String sku, String reason, String correlationId) {
        emit(EventTypes.INVENTORY_REJECTED, orderId, new Payloads.InventoryRejected(orderId, sku, reason), correlationId);
    }

    public void released(UUID orderId, String correlationId) {
        emit(EventTypes.INVENTORY_RELEASED, orderId, new Payloads.InventoryReleased(orderId), correlationId);
    }

    private void emit(String type, UUID orderId, Object payload, String correlationId) {
        outbox.append(Topics.INVENTORY_EVENTS, codec.wrap(type, orderId.toString(), payload, correlationId));
    }
}
