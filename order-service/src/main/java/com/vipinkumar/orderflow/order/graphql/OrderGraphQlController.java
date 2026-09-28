package com.vipinkumar.orderflow.order.graphql;

import com.vipinkumar.orderflow.order.api.OrderResponse;
import com.vipinkumar.orderflow.order.app.OrderNotFoundException;
import com.vipinkumar.orderflow.order.app.OrderService;
import com.vipinkumar.orderflow.order.domain.OrderStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

/** Read-side GraphQL API: lets a client fetch exactly the order fields it needs in one round-trip. */
@Controller
public class OrderGraphQlController {

    private final OrderService service;

    public OrderGraphQlController(OrderService service) {
        this.service = service;
    }

    @QueryMapping
    public OrderResponse order(@Argument UUID id) {
        try {
            return OrderResponse.from(service.get(id));
        } catch (OrderNotFoundException e) {
            return null;
        }
    }

    @QueryMapping
    public OrderPage orders(@Argument OrderStatus status, @Argument Integer page, @Argument Integer size) {
        int p = page == null ? 0 : Math.max(page, 0);
        int s = size == null ? 20 : Math.min(Math.max(size, 1), 100);
        var result = service.list(status, PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new OrderPage(result.map(OrderResponse::from).getContent(), result.getTotalElements());
    }

    public record OrderPage(List<OrderResponse> items, long totalItems) {}
}
