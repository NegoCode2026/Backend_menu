package com.menusaas.products.controller;

import com.menusaas.inventory.dto.AdjustStockRequest;
import com.menusaas.inventory.entity.MovementReason;
import com.menusaas.products.dto.ProductResponse;
import com.menusaas.products.service.ProductService;
import com.menusaas.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Existencias del catálogo servidas bajo /api/inventory (mismas rutas que
 * antes): el stock vive en el producto, que es dueño del módulo products.
 * Así inventory no depende de products y no hay ciclo entre módulos.
 */
@Tag(name = "Inventory", description = "Existencias, alertas y kardex (tenant-scoped)")
@RestController
@RequestMapping("/api/inventory")
@PreAuthorize("@permissions.has('INVENTORY_MANAGE')")
@RequiredArgsConstructor
public class ProductStockController {

    private final ProductService productService;

    @Operation(summary = "Productos con stock bajo o agotado (alerta)")
    @GetMapping("/low-stock")
    public ApiResponse<List<ProductResponse>> lowStock() {
        return ApiResponse.ok(productService.findLowStockMine());
    }

    @Operation(summary = "Ajuste manual de existencias (reposición o conteo)")
    @PostMapping("/adjust")
    public ApiResponse<ProductResponse> adjust(@Valid @RequestBody AdjustStockRequest request) {
        MovementReason reason = request.reason() != null ? request.reason() : MovementReason.ADJUST;
        return ApiResponse.ok("Stock actualizado",
                productService.setStockMine(request.productId(), request.quantity(), reason));
    }
}
