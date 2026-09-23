package com.menusaas.inventory.controller;

import com.menusaas.inventory.dto.AdjustStockRequest;
import com.menusaas.inventory.dto.StockMovementResponse;
import com.menusaas.inventory.entity.MovementReason;
import com.menusaas.inventory.service.InventoryService;
import com.menusaas.products.dto.ProductResponse;
import com.menusaas.products.service.ProductService;
import com.menusaas.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Inventory", description = "Existencias, alertas y kardex (tenant-scoped)")
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final ProductService productService;

    @Operation(summary = "Productos con stock bajo o agotado (alerta)")
    @GetMapping("/low-stock")
    public ApiResponse<List<ProductResponse>> lowStock() {
        return ApiResponse.ok(productService.findLowStockMine());
    }

    @Operation(summary = "Kardex del restaurante (?productId=&page=&size=)")
    @GetMapping("/movements")
    public ApiResponse<Page<StockMovementResponse>> movements(
            @RequestParam(required = false) Long productId,
            @PageableDefault(size = 50, sort = "id") Pageable pageable) {
        return ApiResponse.ok(inventoryService.listMine(productId, pageable));
    }

    @Operation(summary = "Ajuste manual de existencias (reposición o conteo)")
    @PostMapping("/adjust")
    public ApiResponse<ProductResponse> adjust(@Valid @RequestBody AdjustStockRequest request) {
        MovementReason reason = request.reason() != null ? request.reason() : MovementReason.ADJUST;
        return ApiResponse.ok("Stock actualizado",
                productService.setStockMine(request.productId(), request.quantity(), reason));
    }
}
