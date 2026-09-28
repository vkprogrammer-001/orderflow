package com.vipinkumar.orderflow.inventory.api;

import com.vipinkumar.orderflow.inventory.app.ProductCatalog;
import com.vipinkumar.orderflow.inventory.app.ProductView;
import com.vipinkumar.orderflow.inventory.domain.Reservation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ProductController {

    private final ProductCatalog catalog;

    public ProductController(ProductCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/products")
    public List<ProductView> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                  @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return catalog.list(PageRequest.of(page, size, Sort.by("sku"))).getContent();
    }

    @GetMapping("/products/{sku}")
    public ProductView get(@PathVariable String sku) {
        return catalog.get(sku);
    }

    @PutMapping("/products/{sku}/stock")
    public ProductView restock(@PathVariable String sku, @Valid @RequestBody StockUpdate body) {
        return catalog.restock(sku, body.available());
    }

    @GetMapping("/reservations/{orderId}")
    public ResponseEntity<ReservationView> reservation(@PathVariable UUID orderId) {
        return ResponseEntity.of(catalog.reservation(orderId).map(ReservationView::from));
    }

    public record StockUpdate(@Min(0) @Max(1_000_000) int available) {}

    public record ReservationView(UUID orderId, String status, String reason, List<Line> lines) {
        public record Line(String sku, int quantity) {}

        static ReservationView from(Reservation r) {
            return new ReservationView(r.getOrderId(), r.getStatus().name(), r.getReason(),
                    r.getLines().stream().map(l -> new Line(l.getSku(), l.getQuantity())).toList());
        }
    }
}
