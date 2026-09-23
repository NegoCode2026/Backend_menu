package com.menusaas.publicmenu.controller;

import com.menusaas.publicmenu.dto.PublicMenuResponse;
import com.menusaas.publicmenu.service.PublicMenuService;
import com.menusaas.products.service.ProductService;
import com.menusaas.restaurants.dto.DirectoryRestaurantResponse;
import com.menusaas.restaurants.entity.Restaurant;
import com.menusaas.restaurants.service.RestaurantService;
import com.menusaas.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Public Menu", description = "Menú público por slug — sin autenticación")
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicMenuController {

    private final PublicMenuService publicMenuService;
    private final RestaurantService restaurantService;
    private final ProductService productService;

    @Operation(summary = "Obtener el menú público de un restaurante")
    @GetMapping("/menu/{slug}")
    public ApiResponse<PublicMenuResponse> getMenu(@PathVariable String slug) {
        return ApiResponse.ok(publicMenuService.getBySlug(slug));
    }

    @Operation(summary = "Directorio público de restaurantes activos (módulo Explore)")
    @GetMapping("/restaurants")
    public ApiResponse<List<DirectoryRestaurantResponse>> getDirectory() {
        List<Restaurant> restaurants = restaurantService.findAllActiveOrderedByName();
        Map<Long, Long> productCounts = productService.countAvailableGroupedByRestaurant();

        List<DirectoryRestaurantResponse> directory = restaurants.stream()
                .map(r -> new DirectoryRestaurantResponse(
                        r.getId(),
                        r.getName(),
                        r.getSlug(),
                        r.getDescription(),
                        r.getLogoUrl(),
                        r.getPhone(),
                        r.getWhatsapp(),
                        r.getAddress(),
                        r.isOpen(),
                        productCounts.getOrDefault(r.getId(), 0L)))
                .toList();
        return ApiResponse.ok(directory);
    }
}
