package com.menusaas.publicmenu.dto;

import com.menusaas.categories.entity.Category;
import com.menusaas.products.entity.Product;
import com.menusaas.restaurants.entity.Restaurant;

import java.math.BigDecimal;
import java.util.List;

public record PublicMenuResponse(
        RestaurantInfo restaurant,
        List<CategoryInfo> categories
) {

    public static PublicMenuResponse from(RestaurantInfo restaurant, List<CategoryInfo> categories) {
        return new PublicMenuResponse(restaurant, categories);
    }

    public record RestaurantInfo(
            String name,
            String slug,
            String logoUrl,
            String description,
            String phone,
            String address,
            String whatsapp,
            String instagram,
            String facebook,
            boolean open
    ) {
        /**
         * @param logoUrl URL ya resuelta por el llamador (firmada o null).
         */
        public static RestaurantInfo from(Restaurant r, String logoUrl) {
            return new RestaurantInfo(
                    r.getName(), r.getSlug(), logoUrl, r.getDescription(), r.getPhone(),
                    r.getAddress(), r.getWhatsapp(), r.getInstagram(), r.getFacebook(), r.isOpen());
        }
    }

    public record CategoryInfo(
            Long id,
            String name,
            String description,
            int position,
            List<ProductInfo> products
    ) {
        public static CategoryInfo from(Category c, List<ProductInfo> products) {
            return new CategoryInfo(
                    c.getId(), c.getName(), c.getDescription(), c.getPosition(), products);
        }

        public static CategoryInfo uncategorized(List<ProductInfo> products) {
            return new CategoryInfo(0L, "Sin categoría", null, Integer.MAX_VALUE, products);
        }
    }

    public record ProductInfo(
            Long id,
            String name,
            String description,
            BigDecimal price,
            String imageUrl,
            boolean available
    ) {
        /**
         * @param imageUrl URL ya resuelta por el llamador (firmada o null).
         */
        public static ProductInfo from(Product p, String imageUrl) {
            return new ProductInfo(
                    p.getId(), p.getName(), p.getDescription(), p.getPrice(), imageUrl, p.isAvailable());
        }
    }
}