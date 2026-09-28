package com.vipinkumar.orderflow.order.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @EntityGraph(attributePaths = "lines")
    Optional<Order> findWithLinesById(UUID id);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    /** Used by the saga watchdog; backed by the (status, updated_at) index. */
    List<Order> findTop100ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(Collection<OrderStatus> statuses, Instant before);
}
