package com.menusaas.publicmenu.dto;

import com.menusaas.categories.entity.Category;
import com.menusaas.products.entity.Product;
import com.menusaas.restaurants.entity.Restaurant;

import java.math.BigDecimal;
import java.util.List;

public record PublicMenuResponse(
        RestaurantInfo restaurant,
        List<CategoryInfo> categories,
        /** Números de mesa registrados (para validar el QR y el picker). Vacía si no hay. */
        List<String> tables
) {

    public static PublicMenuResponse from(RestaurantInfo restaurant, List<CategoryInfo> categories,
                                          List<String> tables) {
        return new PublicMenuResponse(restaurant, categories, tables != null ? tables : List.of());
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
            /** NIT para la factura que genera el cliente. */
            String taxId,
            /** Lo que el restaurante configuró, p. ej. "20-30 min"; null si no lo fijó. */
            String estimatedPrepTime,
            boolean open
    ) {
        /**
         * @param logoUrl URL ya resuelta por el llamador (firmada o null).
         */
        public static RestaurantInfo from(Restaurant r, String logoUrl) {
            return new RestaurantInfo(
                    r.getName(), r.getSlug(), logoUrl, r.getDescription(), r.getPhone(),
                    r.getAddress(), r.getWhatsapp(), r.getInstagram(), r.getFacebook(),
                    r.getTaxId(), r.getEstimatedPrepTime(), r.isOpen());
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
            boolean available,
            /**
             * Grupos de opciones que ofrece el producto ("Tamaño", "Término").
             * Vacío si no tiene. Sin esto el cliente no puede elegir variantes y
             * la carta no representa lo que el restaurante vende.
             */
            List<ModifierGroupInfo> modifierGroups
    ) {
        public static ProductInfo from(Product p, String imageUrl) {
            return from(p, imageUrl, List.of());
        }

        public static ProductInfo from(Product p, String imageUrl,
                                       List<ModifierGroupInfo> modifierGroups) {
            return new ProductInfo(
                    p.getId(), p.getName(), p.getDescription(), p.getPrice(), imageUrl,
                    p.isAvailable(), modifierGroups);
        }
    }

    /** Grupo de opciones tal como lo ve el cliente: qué elegir y cuántas veces. */
    public record ModifierGroupInfo(
            Long id,
            String name,
            int minSelections,
            int maxSelections,
            boolean required,
            List<ModifierOptionInfo> options
    ) {
    }

    public record ModifierOptionInfo(Long id, String name, BigDecimal priceDelta) {
    }
}