package com.aurora.backend.inventory.service;

import java.util.List;
import java.util.UUID;

import com.aurora.backend.catalog.product.entity.ProductVariant;
import com.aurora.backend.catalog.product.repository.ProductVariantRepository;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.common.exception.NotFoundException;
import com.aurora.backend.inventory.dto.InventoryResponse;
import com.aurora.backend.inventory.dto.InventoryUpdateRequest;
import com.aurora.backend.inventory.dto.StockAdjustmentRequest;
import com.aurora.backend.inventory.dto.StockMovementResponse;
import com.aurora.backend.inventory.entity.Inventory;
import com.aurora.backend.inventory.entity.StockMovement;
import com.aurora.backend.inventory.entity.StockMovementType;
import com.aurora.backend.inventory.repository.InventoryRepository;
import com.aurora.backend.inventory.repository.StockMovementRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminInventoryService {

    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductVariantRepository productVariantRepository;

    public AdminInventoryService(
            InventoryRepository inventoryRepository,
            StockMovementRepository stockMovementRepository,
            ProductVariantRepository productVariantRepository
    ) {
        this.inventoryRepository = inventoryRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.productVariantRepository = productVariantRepository;
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> listInventory() {
        return inventoryRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(InventoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventory(UUID variantId) {
        return InventoryResponse.from(getInventoryByVariantId(variantId));
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponse> listMovements() {
        return stockMovementRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(StockMovementResponse::from)
                .toList();
    }

    @Transactional
    public InventoryResponse updateInventory(UUID variantId, InventoryUpdateRequest request) {
        Inventory inventory = getOrCreateInventory(variantId);
        int movementQuantity = applyInventoryValues(
                inventory,
                request.availableQuantity(),
                request.reservedQuantity(),
                request.lowStockThreshold()
        );

        if (movementQuantity > 0) {
            stockMovementRepository.save(new StockMovement(
                    inventory.getVariant(),
                    StockMovementType.ADJUSTMENT,
                    movementQuantity,
                    "Admin inventory update."
            ));
        }

        return InventoryResponse.from(inventory);
    }

    @Transactional
    public InventoryResponse adjustInventory(UUID variantId, StockAdjustmentRequest request) {
        Inventory inventory = getOrCreateInventory(variantId);
        int movementQuantity = applyInventoryValues(
                inventory,
                request.availableQuantity(),
                request.reservedQuantity(),
                request.lowStockThreshold()
        );

        stockMovementRepository.save(new StockMovement(
                inventory.getVariant(),
                StockMovementType.ADJUSTMENT,
                movementQuantity == 0 ? 1 : movementQuantity,
                normalizeReason(request.reason())
        ));

        return InventoryResponse.from(inventory);
    }

    private Inventory getInventoryByVariantId(UUID variantId) {
        return inventoryRepository.findByVariantId(variantId)
                .orElseThrow(() -> new NotFoundException("Inventory", variantId));
    }

    private Inventory getOrCreateInventory(UUID variantId) {
        return inventoryRepository.findByVariantId(variantId)
                .orElseGet(() -> {
                    ProductVariant variant = productVariantRepository.findById(variantId)
                            .orElseThrow(() -> new NotFoundException("Product variant", variantId));
                    return inventoryRepository.save(new Inventory(variant, 0, 0, DEFAULT_LOW_STOCK_THRESHOLD));
                });
    }

    private int applyInventoryValues(
            Inventory inventory,
            Integer availableQuantity,
            Integer reservedQuantity,
            Integer lowStockThreshold
    ) {
        if (availableQuantity == null && reservedQuantity == null && lowStockThreshold == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVENTORY_UPDATE_EMPTY",
                    "At least one inventory value must be provided."
            );
        }

        int movementQuantity = 0;

        if (availableQuantity != null) {
            movementQuantity += Math.abs(availableQuantity - inventory.getAvailableQuantity());
            inventory.adjustAvailableQuantity(availableQuantity);
        }

        if (reservedQuantity != null) {
            movementQuantity += Math.abs(reservedQuantity - inventory.getReservedQuantity());
            inventory.adjustReservedQuantity(reservedQuantity);
        }

        if (lowStockThreshold != null) {
            inventory.updateLowStockThreshold(lowStockThreshold);
        }

        return movementQuantity;
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Admin stock adjustment.";
        }

        return reason.trim();
    }
}
