package com.example.catalog.model.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * A product in the catalog.
 *
 * <p>The entity owns its invariants: the constructor and {@link #update} are the only ways to set
 * state, so a {@code Product} instance is never half-built. The same rules are enforced a second
 * time by CHECK constraints in the database (see {@code V1__create_products.sql}) — bean validation
 * protects the API, the constraints protect the data from anything that bypasses the API.
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false)
    private int quantity;

    /** Incremented by Hibernate on every update; drives optimistic locking. */
    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Required by JPA. */
    protected Product() {
    }

    public Product(String name, BigDecimal price, String category, int quantity) {
        update(name, price, category, quantity);
    }

    /** Applies a full replacement of the mutable state. */
    public final void update(String name, BigDecimal price, String category, int quantity) {
        this.name = Objects.requireNonNull(name, "name").trim();
        this.price = Objects.requireNonNull(price, "price").setScale(2, RoundingMode.HALF_UP);
        this.category = Objects.requireNonNull(category, "category").trim();
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCategory() {
        return category;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getVersion() {
        return version;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
