package com.menusaas.publicmenu.service;

import com.menusaas.categories.entity.Category;
import com.menusaas.categories.service.CategoryService;
import com.menusaas.shared.security.SignedUrlService;
import com.menusaas.publicmenu.dto.PublicMenuResponse;
import com.menusaas.products.entity.Product;
import com.menusaas.products.service.ProductService;
import com.menusaas.restaurants.entity.Restaurant;
import com.menusaas.restaurants.service.RestaurantService;
import com.menusaas.tables.service.TableService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Menú público: sin autenticación, identificado por slug.
 * Las imágenes se sirven con URLs firmadas con expiración.
 *
 * No toca repositorios ajenos: resuelve todo vía servicios de dominio
 * (RestaurantService, CategoryService, ProductService).
 */
@Service
@RequiredArgsConstructor
public class PublicMenuService {

    private final RestaurantService restaurantService;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final TableService tableService;
    private final SignedUrlService signedUrlService;

    @Transactional(readOnly = true)
    public PublicMenuResponse getBySlug(String slug) {
        Restaurant restaurant = restaurantService.findActiveBySlugOrThrow(slug);

        List<Category> categories = categoryService.findActiveByRestaurantId(restaurant.getId());

        List<PublicMenuResponse.CategoryInfo> categoryInfos = categories.stream()
                .map(category -> {
                    List<PublicMenuResponse.ProductInfo> products = productService
                            .findAvailableByCategory(category.getId(), restaurant.getId())
                            .stream()
                            .map(this::toProductInfo)
                            .toList();
                    return PublicMenuResponse.CategoryInfo.from(category, products);
                })
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

        // Productos sin categoría: grupo final "Sin categoría".
        List<PublicMenuResponse.ProductInfo> loose = productService
                .findUncategorized(restaurant.getId())
                .stream()
                .filter(Product::isAvailable)
                .map(this::toProductInfo)
                .toList();
        if (!loose.isEmpty()) {
            categoryInfos.add(PublicMenuResponse.CategoryInfo.uncategorized(loose));
        }

        return PublicMenuResponse.from(
                PublicMenuResponse.RestaurantInfo.from(
                        restaurant, signedUrlService.toSignedUrlOrNull(restaurant.getLogoUrl())),
                categoryInfos,
                tableService.findNumbersByRestaurantId(restaurant.getId())
        );
    }

    private PublicMenuResponse.ProductInfo toProductInfo(Product p) {
        return PublicMenuResponse.ProductInfo.from(
                p, signedUrlService.toSignedUrlOrNull(p.getImageUrl()));
    }
}
