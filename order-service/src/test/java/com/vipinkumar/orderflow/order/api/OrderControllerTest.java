package com.vipinkumar.orderflow.order.api;

import com.vipinkumar.orderflow.order.app.IdempotencyKeyReusedException;
import com.vipinkumar.orderflow.order.app.OrderNotFoundException;
import com.vipinkumar.orderflow.order.app.OrderService;
import com.vipinkumar.orderflow.order.app.PlacedOrder;
import com.vipinkumar.orderflow.order.domain.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired MockMvc mvc;
    @MockBean OrderService service;

    private static final String BODY = """
            {"customerId":"cust-1","paymentMethod":"CARD","lines":[{"sku":"CHARGER-35W","quantity":2}]}
            """;

    @Test
    void acceptsAnOrderAndPointsAtIt() throws Exception {
        Order order = Order.place("cust-1", "CARD", Map.of("CHARGER-35W", 2), Instant.now());
        when(service.place(any(), eq("key-12345"))).thenReturn(new PlacedOrder(order, false));

        mvc.perform(post("/api/v1/orders").header("Idempotency-Key", "key-12345")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "http://localhost/api/v1/orders/" + order.getId()))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void requiresAnIdempotencyKey() throws Exception {
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validatesTheBody() throws Exception {
        mvc.perform(post("/api/v1/orders").header("Idempotency-Key", "key-12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"cust-1\",\"paymentMethod\":\"CASH\",\"lines\":[{\"sku\":\"X\",\"quantity\":0}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reusedKeyIsUnprocessable() throws Exception {
        when(service.place(any(), eq("key-12345"))).thenThrow(new IdempotencyKeyReusedException("key-12345"));

        mvc.perform(post("/api/v1/orders").header("Idempotency-Key", "key-12345")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Idempotency key reused"));
    }

    @Test
    void unknownOrderIs404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenThrow(new OrderNotFoundException(id));

        mvc.perform(get("/api/v1/orders/{id}", id)).andExpect(status().isNotFound());
    }
}
