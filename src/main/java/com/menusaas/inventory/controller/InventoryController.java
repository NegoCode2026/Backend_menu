package com.menusaas.inventory.controller;

import com.menusaas.inventory.dto.AdjustIngredientRequest;
import com.menusaas.inventory.dto.IngredientRequest;
import com.menusaas.inventory.dto.IngredientResponse;
import com.menusaas.inventory.dto.StockMovementResponse;
import com.menusaas.inventory.entity.MovementReason;
import com.menusaas.inventory.service.InventoryService;
import com.menusaas.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Inventory", description = "Existencias, alertas y kardex (tenant-scoped)")
@RestController
@RequestMapping("/api/inventory")
@PreAuthorize("@permissions.has('INVENTORY_MANAGE')")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @Operation(summary = "Kardex del restaurante (?productId=&page=&size=)")
    @GetMapping("/movements")
    public ApiResponse<Page<StockMovementResponse>> movements(
            @RequestParam(required = false) Long productId,
            @PageableDefault(size = 50, sort = "id") Pageable pageable) {
        return ApiResponse.ok(inventoryService.listMine(productId, pageable));
    }

    @Operation(summary = "Alertas de ingredientes con stock bajo")
    @GetMapping("/ingredients/low-stock")
    public ApiResponse<List<IngredientResponse>> lowStockIngredients() {
        return ApiResponse.ok(inventoryService.lowStockIngredientsMine());
    }

    @Operation(summary = "Listar ingredientes")
    @GetMapping("/ingredients")
    public ApiResponse<List<IngredientResponse>> ingredients() {
        return ApiResponse.ok(inventoryService.listIngredientsMine());
    }

    @Operation(summary = "Crear ingrediente")
    @PostMapping("/ingredients")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IngredientResponse> createIngredient(@Valid @RequestBody IngredientRequest request) {
        return ApiResponse.ok("Ingrediente creado", inventoryService.createIngredientMine(request));
    }

    @Operation(summary = "Actualizar ingrediente")
    @PutMapping("/ingredients/{id}")
    public ApiResponse<IngredientResponse> updateIngredient(
            @PathVariable Long id, @Valid @RequestBody IngredientRequest request) {
        return ApiResponse.ok("Ingrediente actualizado", inventoryService.updateIngredientMine(id, request));
    }

    @Operation(summary = "Eliminar ingrediente")
    @DeleteMapping("/ingredients/{id}")
    public ApiResponse<Void> deleteIngredient(@PathVariable Long id) {
        inventoryService.deleteIngredientMine(id);
        return ApiResponse.ok("Ingrediente eliminado");
    }

    @Operation(summary = "Ajustar stock de ingrediente a cantidad absoluta")
    @PostMapping("/ingredients/{id}/adjust")
    public ApiResponse<IngredientResponse> adjustIngredient(
            @PathVariable Long id, @Valid @RequestBody AdjustIngredientRequest request) {
        return ApiResponse.ok("Stock actualizado", inventoryService.adjustIngredientMine(
                id, request.quantity(), request.reason() != null ? request.reason() : MovementReason.ADJUST));
    }
}
