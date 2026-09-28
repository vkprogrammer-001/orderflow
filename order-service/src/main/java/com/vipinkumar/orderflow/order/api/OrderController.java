package com.vipinkumar.orderflow.order.api;

import com.vipinkumar.orderflow.order.app.OrderService;
import com.vipinkumar.orderflow.order.app.PlacedOrder;
import com.vipinkumar.orderflow.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @Operation(summary = "Place an order",
            description = "Returns 202: the order is accepted and moves through the saga asynchronously. "
                    + "Retrying with the same Idempotency-Key returns the original order.")
    @PostMapping
    public ResponseEntity<OrderResponse> place(
            @Parameter(description = "Client-generated unique key, e.g. a UUID")
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 100) @Pattern(regexp = "[A-Za-z0-9_-]+") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        PlacedOrder placed = service.place(request, idempotencyKey);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(placed.order().getId()).toUri();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(location)
                .header("Idempotent-Replayed", String.valueOf(placed.replayed()))
                .body(OrderResponse.from(placed.order()));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return OrderResponse.from(service.get(id));
    }

    @GetMapping
    public PageResponse<OrderResponse> list(@RequestParam(required = false) OrderStatus status,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var result = service.list(status, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResponse<>(result.map(OrderResponse::from).getContent(), result.getNumber(),
                result.getSize(), result.getTotalElements());
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalItems) {}
}
