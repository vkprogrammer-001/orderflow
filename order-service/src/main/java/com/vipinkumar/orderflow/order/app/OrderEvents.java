package com.vipinkumar.orderflow.order.app;

import com.vipinkumar.orderflow.events.EventCodec;
import com.vipinkumar.orderflow.events.EventTypes;
import com.vipinkumar.orderflow.events.Payloads;
import com.vipinkumar.orderflow.events.Topics;
import com.vipinkumar.orderflow.order.domain.Order;
import com.vipinkumar.orderflow.order.domain.OrderStatus;
import com.vipinkumar.orderflow.outbox.OutboxWriter;
import org.springframework.stereotype.Component;

import java.util.List;

/** Thin facade over the outbox so the service and saga speak in domain terms, not topics. */
@Component
public class OrderEvents {

    private final OutboxWriter outbox;
    private final EventCodec codec;

    public OrderEvents(OutboxWriter outbox, EventCodec codec) {
        this.outbox = outbox;
        this.codec = codec;
    }

    public void statusChanged(Order order, OrderStatus from) {
        String id = order.getId().toString();
        outbox.append(Topics.ORDER_EVENTS, codec.wrap(EventTypes.ORDER_STATUS_CHANGED, id,
                new Payloads.OrderStatusChanged(order.getId(), from == null ? null : from.name(),
                        order.getStatus().name(), order.getStatusReason()), id));
    }

    public void reserveInventory(Order order) {
        String id = order.getId().toString();
        List<Payloads.Line> lines = order.getLines().stream()
                .map(l -> new Payloads.Line(l.getSku(), l.getQuantity())).toList();
        outbox.append(Topics.INVENTORY_COMMANDS, codec.wrap(EventTypes.RESERVE_INVENTORY, id,
                new Payloads.ReserveInventory(order.getId(), lines), id));
    }

    public void releaseInventory(Order order, String reason) {
        String id = order.getId().toString();
        outbox.append(Topics.INVENTORY_COMMANDS, codec.wrap(EventTypes.RELEASE_INVENTORY, id,
                new Payloads.ReleaseInventory(order.getId(), reason), id));
    }

    public void authorizePayment(Order order) {
        String id = order.getId().toString();
        outbox.append(Topics.PAYMENT_COMMANDS, codec.wrap(EventTypes.AUTHORIZE_PAYMENT, id,
                new Payloads.AuthorizePayment(order.getId(), order.getCustomerId(), order.getTotalAmount(),
                        order.getCurrency(), order.getPaymentMethod()), id));
    }
}
