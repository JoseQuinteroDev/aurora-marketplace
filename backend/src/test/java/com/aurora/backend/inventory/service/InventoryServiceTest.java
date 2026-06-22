package com.aurora.backend.inventory.service;

import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.catalog.product.entity.ProductVariant;
import com.aurora.backend.catalog.product.repository.ProductVariantRepository;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.inventory.entity.Inventory;
import com.aurora.backend.inventory.entity.StockMovementType;
import com.aurora.backend.inventory.repository.InventoryRepository;
import com.aurora.backend.inventory.repository.StockMovementRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InventoryService} stock invariants (integrity): quantities
 * must be positive, available stock can never go negative, and reserve/release keep
 * the available/reserved split consistent.
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    private static final UUID VARIANT_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");

    @Mock private InventoryRepository inventoryRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private ProductVariantRepository productVariantRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private ProductVariant variant() {
        return new ProductVariant("SKU-1", "Default", null, null, true);
    }

    private void stockExists(Inventory inventory) {
        when(productVariantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant()));
        when(inventoryRepository.findByVariantId(VARIANT_ID)).thenReturn(Optional.of(inventory));
    }

    @Test
    void nonPositiveQuantityIsRejected() {
        assertThatThrownBy(() -> inventoryService.recordStockMovement(VARIANT_ID, StockMovementType.IN, 0, "x"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_STOCK_QUANTITY"));
    }

    @Test
    void inboundMovementIncreasesAvailable() {
        stockExists(new Inventory(variant(), 5, 0, 2));

        Inventory result = inventoryService.recordStockMovement(VARIANT_ID, StockMovementType.IN, 10, "restock");

        assertThat(result.getAvailableQuantity()).isEqualTo(15);
    }

    @Test
    void saleDecreasesAvailable() {
        stockExists(new Inventory(variant(), 10, 0, 2));

        Inventory result = inventoryService.recordStockMovement(VARIANT_ID, StockMovementType.SALE, 3, "order");

        assertThat(result.getAvailableQuantity()).isEqualTo(7);
    }

    @Test
    void outboundBeyondAvailableIsRejected() {
        stockExists(new Inventory(variant(), 2, 0, 2));

        assertThatThrownBy(() -> inventoryService.recordStockMovement(VARIANT_ID, StockMovementType.OUT, 5, "x"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INSUFFICIENT_STOCK"));
    }

    @Test
    void reserveMovesUnitsFromAvailableToReserved() {
        stockExists(new Inventory(variant(), 10, 0, 2));

        Inventory result = inventoryService.recordStockMovement(VARIANT_ID, StockMovementType.RESERVE, 4, "hold");

        assertThat(result.getAvailableQuantity()).isEqualTo(6);
        assertThat(result.getReservedQuantity()).isEqualTo(4);
    }

    @Test
    void releasingMoreThanReservedIsRejected() {
        stockExists(new Inventory(variant(), 10, 1, 2));

        assertThatThrownBy(() -> inventoryService.recordStockMovement(VARIANT_ID, StockMovementType.RELEASE, 5, "x"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INSUFFICIENT_RESERVED_STOCK"));
    }
}
