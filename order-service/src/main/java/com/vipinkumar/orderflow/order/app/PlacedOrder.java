package com.vipinkumar.orderflow.order.app;

import com.vipinkumar.orderflow.order.domain.Order;

/** Result of placing an order; {@code replayed} is true when an earlier request with the same key won. */
public record PlacedOrder(Order order, boolean replayed) {}
