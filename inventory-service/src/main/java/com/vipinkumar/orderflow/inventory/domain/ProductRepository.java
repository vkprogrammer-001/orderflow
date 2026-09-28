package com.vipinkumar.orderflow.inventory.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, String> {

    /**
     * Atomic check-and-decrement. The WHERE clause makes overselling impossible even under heavy
     * concurrency: two buyers racing for the last unit both run this, exactly one gets rowcount 1.
     * No read-modify-write, no lost update, no explicit lock held across a network call.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE products
               SET available = available - :qty, reserved = reserved + :qty,
                   version = version + 1, updated_at = now()
             WHERE sku = :sku AND available >= :qty
            """, nativeQuery = true)
    int tryReserve(@Param("sku") String sku, @Param("qty") int qty);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE products
               SET available = available + :qty, reserved = reserved - :qty,
                   version = version + 1, updated_at = now()
             WHERE sku = :sku AND reserved >= :qty
            """, nativeQuery = true)
    int release(@Param("sku") String sku, @Param("qty") int qty);
}
