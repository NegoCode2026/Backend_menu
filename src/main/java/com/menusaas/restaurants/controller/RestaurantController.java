package com.menusaas.restaurants.controller;

import com.menusaas.restaurants.dto.RestaurantOpenRequest;
import com.menusaas.restaurants.dto.RestaurantRequest;
import com.menusaas.restaurants.dto.RestaurantResponse;
import com.menusaas.restaurants.service.RestaurantService;
import com.menusaas.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Restaurants", description = "Configuración del restaurante (tenant-scoped)")
@RestController
@RequestMapping("/api/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;

    @Operation(summary = "Obtener mi restaurante (del JWT)")
    @GetMapping("/me")
    public ApiResponse<RestaurantResponse> getMine() {
        return ApiResponse.ok(restaurantService.getMine());
    }

    @Operation(summary = "Actualizar mi restaurante")
    @PutMapping("/me")
    @PreAuthorize("@permissions.has('SETTINGS_EDIT')")
    public ApiResponse<RestaurantResponse> updateMine(@Valid @RequestBody RestaurantRequest request) {
        return ApiResponse.ok("Restaurante actualizado", restaurantService.updateMine(request));
    }

    @Operation(summary = "Abrir o cerrar mi restaurante (bloquea nuevos pedidos al cerrar)")
    @PatchMapping("/me/open")
    @PreAuthorize("@permissions.has('SETTINGS_EDIT')")
    public ApiResponse<RestaurantResponse> setOpenMine(@Valid @RequestBody RestaurantOpenRequest request) {
        return ApiResponse.ok("Estado actualizado", restaurantService.setOpenMine(request.open()));
    }
}
