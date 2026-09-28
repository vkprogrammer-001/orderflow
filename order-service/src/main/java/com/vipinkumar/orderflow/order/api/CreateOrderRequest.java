package com.vipinkumar.orderflow.order.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Clients send SKUs and quantities only. Prices are resolved server-side by inventory. */
public record CreateOrderRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String customerId,
        @NotBlank @Pattern(regexp = "CARD|UPI|WALLET", message = "must be CARD, UPI or WALLET") String paymentMethod,
        @NotEmpty @Size(max = 50) List<@Valid Line> lines) {

    public record Line(
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Z0-9-]+") String sku,
            @Min(1) @Max(100) int quantity) {}
}
