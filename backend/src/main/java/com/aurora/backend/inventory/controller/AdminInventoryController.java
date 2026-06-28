package com.aurora.backend.inventory.controller;

import java.util.List;
import java.util.UUID;

import com.aurora.backend.common.api.ApiResponse;
import com.aurora.backend.inventory.dto.InventoryResponse;
import com.aurora.backend.inventory.dto.InventoryUpdateRequest;
import com.aurora.backend.inventory.dto.StockAdjustmentRequest;
import com.aurora.backend.inventory.dto.StockMovementResponse;
import com.aurora.backend.inventory.service.AdminInventoryService;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/inventory")
@PreAuthorize("hasRole('ADMIN')")
public class AdminInventoryController {

    private final AdminInventoryService adminInventoryService;

    public AdminInventoryController(AdminInventoryService adminInventoryService) {
        this.adminInventoryService = adminInventoryService;
    }

    @GetMapping
    public ApiResponse<List<InventoryResponse>> listInventory() {
        return ApiResponse.success("Inventory retrieved successfully.", adminInventoryService.listInventory());
    }

    @GetMapping("/{variantId}")
    public ApiResponse<InventoryResponse> getInventory(@PathVariable UUID variantId) {
        return ApiResponse.success("Inventory retrieved successfully.", adminInventoryService.getInventory(variantId));
    }

    @PatchMapping("/{variantId}")
    public ApiResponse<InventoryResponse> updateInventory(
            @PathVariable UUID variantId,
            @Valid @RequestBody InventoryUpdateRequest request
    ) {
        return ApiResponse.success("Inventory updated successfully.", adminInventoryService.updateInventory(variantId, request));
    }

    @GetMapping("/movements")
    public ApiResponse<List<StockMovementResponse>> listMovements() {
        return ApiResponse.success("Stock movements retrieved successfully.", adminInventoryService.listMovements());
    }

    @PostMapping("/{variantId}/adjust")
    public ApiResponse<InventoryResponse> adjustInventory(
            @PathVariable UUID variantId,
            @Valid @RequestBody StockAdjustmentRequest request
    ) {
        return ApiResponse.success("Inventory adjusted successfully.", adminInventoryService.adjustInventory(variantId, request));
    }
}
