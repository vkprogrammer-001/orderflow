package com.vipinkumar.orderflow.inventory.app;

import com.vipinkumar.orderflow.inventory.domain.ProductRepository;
import com.vipinkumar.orderflow.inventory.domain.Reservation;
import com.vipinkumar.orderflow.inventory.domain.ReservationRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProductCatalog {

    public static final String CACHE = "products";

    private final ProductRepository products;
    private final ReservationRepository reservations;
    private final Clock clock;

    public ProductCatalog(ProductRepository products, ReservationRepository reservations, Clock clock) {
        this.products = products;
        this.reservations = reservations;
        this.clock = clock;
    }

    /** Hot read path (product pages): served from Redis, evicted whenever stock changes. */
    @Cacheable(cacheNames = CACHE, key = "#sku")
    @Transactional(readOnly = true)
    public ProductView get(String sku) {
        return products.findById(sku).map(ProductView::from)
                .orElseThrow(() -> new NoSuchElementException("Product " + sku + " was not found"));
    }

    @Transactional(readOnly = true)
    public Page<ProductView> list(Pageable pageable) {
        return products.findAll(pageable).map(ProductView::from);
    }

    @CacheEvict(cacheNames = CACHE, key = "#sku")
    @Transactional
    public ProductView restock(String sku, int available) {
        var product = products.findById(sku)
                .orElseThrow(() -> new NoSuchElementException("Product " + sku + " was not found"));
        product.restock(available, clock.instant());
        return ProductView.from(product);
    }

    @Transactional(readOnly = true)
    public Optional<Reservation> reservation(UUID orderId) {
        return reservations.findById(orderId);
    }
}
