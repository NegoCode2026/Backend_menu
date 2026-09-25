package com.menusaas.products.controller;

import com.menusaas.inventory.dto.RecipeItemResponse;
import com.menusaas.inventory.dto.RecipeLineRequest;
import com.menusaas.products.dto.ProductRequest;
import com.menusaas.products.dto.ProductResponse;
import com.menusaas.products.service.ProductService;
import com.menusaas.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Products", description = "Productos del menú (siempre del restaurante del JWT)")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "Listar mis productos con paginación (opcional: filtrar por categoría)")
    @GetMapping
    public ApiResponse<Page<ProductResponse>> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(productService.listMine(categoryId,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.ASC, "position"))));
    }

    @Operation(summary = "Obtener un producto")
    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(productService.getMine(id));
    }

    @Operation(summary = "Crear producto")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissions.has('MENU_EDIT')")
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ApiResponse.ok("Producto creado", productService.createMine(request));
    }

    @Operation(summary = "Actualizar producto")
    @PutMapping("/{id}")
    @PreAuthorize("@permissions.has('MENU_EDIT')")
    public ApiResponse<ProductResponse> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ApiResponse.ok("Producto actualizado", productService.updateMine(id, request));
    }

    @Operation(summary = "Eliminar producto")
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissions.has('MENU_EDIT')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productService.deleteMine(id);
        return ApiResponse.ok("Producto eliminado");
    }

    @Operation(summary = "Ver receta del plato (ingredientes por unidad)")
    @GetMapping("/{id}/recipe")
    @PreAuthorize("@permissions.has('MENU_EDIT')")
    public ApiResponse<java.util.List<RecipeItemResponse>> getRecipe(@PathVariable Long id) {
        return ApiResponse.ok(productService.getRecipeMine(id));
    }

    @Operation(summary = "Reemplazar receta del plato (vacía = quitar receta)")
    @PutMapping("/{id}/recipe")
    @PreAuthorize("@permissions.has('MENU_EDIT')")
    public ApiResponse<java.util.List<RecipeItemResponse>> setRecipe(
            @PathVariable Long id,
            @Valid @RequestBody java.util.List<RecipeLineRequest> lines) {
        productService.setRecipeMine(id, lines);
        return ApiResponse.ok("Receta actualizada", productService.getRecipeMine(id));
    }
}
