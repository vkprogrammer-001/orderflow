package com.vipinkumar.orderflow.inventory.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 200 buyers race for the 3 units of RIBBON-CABLE seeded by Flyway. Exactly 3 must win and stock
 * must never go negative. This is the property the conditional UPDATE exists to guarantee.
 */
@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StockConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired ProductRepository products;
    @Autowired PlatformTransactionManager txManager;

    @Test
    void neverOversellsUnderContention() throws Exception {
        var tx = new TransactionTemplate(txManager);
        int buyers = 200;
        var start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < buyers; i++) {
                Callable<Integer> buy = () -> {
                    start.await();
                    return tx.execute(s -> products.tryReserve("RIBBON-CABLE", 1));
                };
                results.add(pool.submit(buy));
            }
            start.countDown();
            int winners = 0;
            for (var r : results) winners += r.get();
            assertThat(winners).isEqualTo(3);
        }

        Product after = products.findById("RIBBON-CABLE").orElseThrow();
        assertThat(after.getAvailable()).isZero();
        assertThat(after.getReserved()).isEqualTo(3);
    }
}
