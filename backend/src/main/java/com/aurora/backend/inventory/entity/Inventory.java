package com.aurora.backend.inventory.entity;

import java.time.Instant;
import java.util.UUID;

import com.aurora.backend.catalog.product.entity.ProductVariant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false, unique = true)
    private ProductVariant variant;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Inventory() {
    }

    public Inventory(ProductVariant variant, int availableQuantity, int reservedQuantity, int lowStockThreshold) {
        this.variant = variant;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = reservedQuantity;
        this.lowStockThreshold = lowStockThreshold;
    }

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public ProductVariant getVariant() {
        return variant;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public int getLowStockThreshold() {
        return lowStockThreshold;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void adjustAvailableQuantity(int availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public void adjustReservedQuantity(int reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public void updateLowStockThreshold(int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }
}
