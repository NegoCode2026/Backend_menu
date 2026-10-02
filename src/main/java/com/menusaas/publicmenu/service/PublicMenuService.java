package com.menusaas.publicmenu.service;

import com.menusaas.categories.entity.Category;
import com.menusaas.categories.service.CategoryService;
import com.menusaas.shared.security.SignedUrlService;
import com.menusaas.publicmenu.dto.PublicMenuResponse;
import com.menusaas.publicmenu.dto.PublicRestaurantResponse;
import com.menusaas.products.entity.Product;
import com.menusaas.products.service.ProductService;
import com.menusaas.restaurants.entity.Restaurant;
import com.menusaas.restaurants.service.RestaurantService;
import com.menusaas.tables.service.TableService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

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
    @Cacheable(value = "publicMenu", key = "'listRestaurants'", unless = "#result == null")
    public List<PublicRestaurantResponse> listRestaurants() {
        return restaurantService.listActivePublic().stream()
                .map(r -> PublicRestaurantResponse.from(
                        r, signedUrlService.toSignedUrlOrNull(r.getLogoUrl())))
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "publicMenu", key = "#slug", unless = "#result == null")
    public PublicMenuResponse getBySlug(String slug) {
        Restaurant restaurant = restaurantService.findActiveBySlugOrThrow(slug);

        List<Category> categories = categoryService.findActiveByRestaurantId(restaurant.getId());

        // Una sola query de productos y agrupación en memoria. Antes se
        // consultaba categoría por categoría: 1+N queries en el endpoint público
        // de más tráfico, y el N se dispara con cada categoría que añade el
        // restaurante.
        List<Product> available = productService.findAvailableByRestaurantId(restaurant.getId());

        Map<Long, List<Product>> byCategory = available.stream()
                .filter(p -> p.getCategoryId() != null)
                .collect(java.util.stream.Collectors.groupingBy(Product::getCategoryId));

        List<PublicMenuResponse.CategoryInfo> categoryInfos = categories.stream()
                .map(category -> {
                    List<PublicMenuResponse.ProductInfo> products = byCategory
                            .getOrDefault(category.getId(), List.of())
                            .stream()
                            .map(this::toProductInfo)
                            .toList();
                    return PublicMenuResponse.CategoryInfo.from(category, products);
                })
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

        // Productos sin categoría: grupo final "Sin categoría". Salen del mismo
        // fetch, así que no cuesta una query extra.
        List<PublicMenuResponse.ProductInfo> loose = available.stream()
                .filter(p -> p.getCategoryId() == null)
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
