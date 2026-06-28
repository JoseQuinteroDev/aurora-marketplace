package com.aurora.backend.batch.tasklet;

import java.nio.file.Path;

import com.aurora.backend.batch.dto.InventorySyncLine;
import com.aurora.backend.batch.support.BatchFileReader;
import com.aurora.backend.catalog.product.entity.ProductVariant;
import com.aurora.backend.catalog.product.repository.ProductVariantRepository;
import com.aurora.backend.inventory.entity.Inventory;
import com.aurora.backend.inventory.entity.StockMovement;
import com.aurora.backend.inventory.entity.StockMovementType;
import com.aurora.backend.inventory.repository.InventoryRepository;
import com.aurora.backend.inventory.repository.StockMovementRepository;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SyncInventoryTasklet implements Tasklet {

    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;

    private final BatchFileReader batchFileReader;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final String syncInventoryFile;

    public SyncInventoryTasklet(
            BatchFileReader batchFileReader,
            ProductVariantRepository productVariantRepository,
            InventoryRepository inventoryRepository,
            StockMovementRepository stockMovementRepository,
            @Value("${app.batch.files.sync-inventory}") String syncInventoryFile
    ) {
        this.batchFileReader = batchFileReader;
        this.productVariantRepository = productVariantRepository;
        this.inventoryRepository = inventoryRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.syncInventoryFile = syncInventoryFile;
    }

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        for (InventorySyncLine line : batchFileReader.readInventorySyncLines(Path.of(syncInventoryFile))) {
            syncLine(line);
        }

        return RepeatStatus.FINISHED;
    }

    private void syncLine(InventorySyncLine line) {
        ProductVariant variant = productVariantRepository.findBySku(line.sku())
                .orElseThrow(() -> new IllegalStateException("Variant SKU not found: " + line.sku()));

        // Take the SAME pessimistic row lock the checkout SALE path uses (findByVariantIdForUpdate)
        // rather than an unlocked read: this serializes the batch and concurrent checkouts on the
        // row instead of racing the @Version optimistic lock (which, post-V13, would otherwise abort
        // the whole run on the first concurrent sale). A brand-new variant still falls through to a
        // fresh save.
        Inventory inventory = inventoryRepository.findByVariantIdForUpdate(variant.getId())
                .orElseGet(() -> inventoryRepository.save(new Inventory(
                        variant,
                        0,
                        0,
                        DEFAULT_LOW_STOCK_THRESHOLD
                )));

        int movementQuantity = Math.abs(line.availableQuantity() - inventory.getAvailableQuantity());
        inventory.adjustAvailableQuantity(line.availableQuantity());

        if (line.lowStockThreshold() != null) {
            inventory.updateLowStockThreshold(line.lowStockThreshold());
        }

        stockMovementRepository.save(new StockMovement(
                variant,
                StockMovementType.ADJUSTMENT,
                movementQuantity == 0 ? 1 : movementQuantity,
                "Batch inventory sync."
        ));
    }
}
