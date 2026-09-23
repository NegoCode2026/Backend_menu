package com.menusaas.publicmenu.service;

import com.menusaas.categories.entity.Category;
import com.menusaas.categories.service.CategoryService;
import com.menusaas.shared.security.SignedUrlService;
import com.menusaas.publicmenu.dto.PublicMenuResponse;
import com.menusaas.products.entity.Product;
import com.menusaas.products.service.ProductService;
import com.menusaas.restaurants.entity.Restaurant;
import com.menusaas.restaurants.service.RestaurantService;
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
                    return new PublicMenuResponse.CategoryInfo(
                            category.getId(), category.getName(), category.getDescription(),
                            category.getPosition(), products
                    );
                })
                .toList();

        return new PublicMenuResponse(
                new PublicMenuResponse.RestaurantInfo(
                        restaurant.getName(), restaurant.getSlug(),
                        signedUrlService.toSignedUrlOrNull(restaurant.getLogoUrl()),
                        restaurant.getDescription(), restaurant.getPhone(), restaurant.getAddress(),
                        restaurant.getWhatsapp(), restaurant.getInstagram(), restaurant.getFacebook(),
                        restaurant.isOpen()
                ),
                categoryInfos
        );
    }

    private PublicMenuResponse.ProductInfo toProductInfo(Product p) {
        return new PublicMenuResponse.ProductInfo(
                p.getId(), p.getName(), p.getDescription(), p.getPrice(),
                signedUrlService.toSignedUrlOrNull(p.getImageUrl()), p.isAvailable()
        );
    }
}
