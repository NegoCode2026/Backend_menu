package com.menusaas.publicmenu.service;

import com.menusaas.categories.entity.Category;
import com.menusaas.modifiers.entity.Modifier;
import com.menusaas.modifiers.entity.ModifierGroup;
import com.menusaas.modifiers.entity.ProductModifierGroup;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private final com.menusaas.modifiers.service.ModifierAdminService modifierAdmin;

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

        List<Product> available = productService.findAvailableByRestaurantId(restaurant.getId());
        Map<Long, List<PublicMenuResponse.ModifierGroupInfo>> groupsByProduct =
                modifierAdmin.groupsForProducts(restaurant.getId(),
                                available.stream().map(Product::getId).toList())
                        .entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                                e -> e.getValue().stream()
                                        .map(g -> new PublicMenuResponse.ModifierGroupInfo(
                                                g.id(), g.name(), g.minSelections(), g.maxSelections(),
                                                g.required(),
                                                g.options().stream()
                                                        .map(o -> new PublicMenuResponse.ModifierOptionInfo(
                                                                o.id(), o.name(), o.priceDelta()))
                                                        .toList()))
                                        .toList()));

        // Una sola query de productos y agrupación en memoria. Antes se
        // consultaba categoría por categoría: 1+N queries en el endpoint público
        // de más tráfico, y el N se dispara con cada categoría que añade el
        // restaurante.
        Map<Long, List<Product>> byCategory = available.stream()
                .filter(p -> p.getCategoryId() != null)
                .collect(java.util.stream.Collectors.groupingBy(Product::getCategoryId));

        List<PublicMenuResponse.CategoryInfo> categoryInfos = categories.stream()
                .map(category -> {
                    List<PublicMenuResponse.ProductInfo> products = byCategory
                            .getOrDefault(category.getId(), List.of())
                            .stream()
                            .map(p -> toProductInfo(p, groupsByProduct))
                            .toList();
                    return PublicMenuResponse.CategoryInfo.from(category, products);
                })
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

        // Productos sin categoría: grupo final "Sin categoría". Salen del mismo
        // fetch, así que no cuesta una query extra.
        List<PublicMenuResponse.ProductInfo> loose = available.stream()
                .filter(p -> p.getCategoryId() == null)
                .map(p -> toProductInfo(p, groupsByProduct))
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

    /**
     * @param groupsByProduct grupos de opciones por producto, cargados en un
     *                        único fetch para no reintroducir un N+1 aquí.
     */
    private PublicMenuResponse.ProductInfo toProductInfo(Product p,
            Map<Long, List<PublicMenuResponse.ModifierGroupInfo>> groupsByProduct) {
        return PublicMenuResponse.ProductInfo.from(
                p, signedUrlService.toSignedUrlOrNull(p.getImageUrl()),
                groupsByProduct.getOrDefault(p.getId(), List.of()));
    }

}
