package com.aurora.backend.inventory.service;

import java.util.UUID;

import com.aurora.backend.catalog.product.entity.ProductVariant;
import com.aurora.backend.catalog.product.repository.ProductVariantRepository;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.common.exception.NotFoundException;
import com.aurora.backend.inventory.entity.Inventory;
import com.aurora.backend.inventory.entity.StockMovement;
import com.aurora.backend.inventory.entity.StockMovementType;
import com.aurora.backend.inventory.repository.InventoryRepository;
import com.aurora.backend.inventory.repository.StockMovementRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductVariantRepository productVariantRepository;

    public InventoryService(
            InventoryRepository inventoryRepository,
            StockMovementRepository stockMovementRepository,
            ProductVariantRepository productVariantRepository
    ) {
        this.inventoryRepository = inventoryRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.productVariantRepository = productVariantRepository;
    }

    @Transactional
    public void initializeInventory(ProductVariant variant) {
        if (!inventoryRepository.existsByVariantId(variant.getId())) {
            inventoryRepository.save(new Inventory(variant, 0, 0, DEFAULT_LOW_STOCK_THRESHOLD));
        }
    }

    @Transactional
    public Inventory recordStockMovement(UUID variantId, StockMovementType type, int quantity, String reason) {
        if (quantity <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STOCK_QUANTITY", "Quantity must be positive.");
        }

        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new NotFoundException("Product variant", variantId));

        Inventory inventory = inventoryRepository.findByVariantId(variantId)
                .orElseGet(() -> inventoryRepository.save(
                        new Inventory(variant, 0, 0, DEFAULT_LOW_STOCK_THRESHOLD)
                ));

        applyMovement(inventory, type, quantity);
        stockMovementRepository.save(new StockMovement(variant, type, quantity, reason));
        return inventory;
    }

    private void applyMovement(Inventory inventory, StockMovementType type, int quantity) {
        switch (type) {
            case IN -> inventory.adjustAvailableQuantity(inventory.getAvailableQuantity() + quantity);
            case OUT -> decreaseAvailable(inventory, quantity);
            case RESERVE -> reserve(inventory, quantity);
            case RELEASE -> release(inventory, quantity);
            case ADJUSTMENT -> inventory.adjustAvailableQuantity(quantity);
        }
    }

    private void decreaseAvailable(Inventory inventory, int quantity) {
        if (inventory.getAvailableQuantity() < quantity) {
            throw new BusinessException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Not enough available stock.");
        }

        inventory.adjustAvailableQuantity(inventory.getAvailableQuantity() - quantity);
    }

    private void reserve(Inventory inventory, int quantity) {
        decreaseAvailable(inventory, quantity);
        inventory.adjustReservedQuantity(inventory.getReservedQuantity() + quantity);
    }

    private void release(Inventory inventory, int quantity) {
        if (inventory.getReservedQuantity() < quantity) {
            throw new BusinessException(HttpStatus.CONFLICT, "INSUFFICIENT_RESERVED_STOCK", "Not enough reserved stock.");
        }

        inventory.adjustReservedQuantity(inventory.getReservedQuantity() - quantity);
        inventory.adjustAvailableQuantity(inventory.getAvailableQuantity() + quantity);
    }
}
