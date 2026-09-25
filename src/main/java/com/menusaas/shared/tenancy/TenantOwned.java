package com.menusaas.shared.tenancy;

/**
 * Marca las entidades que pertenecen a un restaurante (columna
 * {@code restaurant_id}). Los repositorios de estas entidades solo deben
 * exponer consultas con tenant (ver {@code TenantRepositoriesTest}).
 *
 * No incluye a {@code User}: su tenant es una relación
 * ({@code @ManyToOne Restaurant}) y se filtra comparando
 * {@code user.getRestaurant().getId()}.
 *
 * Sin tenant propio (acceso solo vía un service que valida pertenencia):
 * {@code OrderItem} y {@code OrderStatusHistory} (vía {@code order_id}),
 * {@code RecipeItem} (vía {@code product_id}/{@code ingredient_id}),
 * {@code StoredFileEntity} (IDs no adivinables + URL firmada) y
 * {@code AuditLog} (solo backoffice).
 */
public interface TenantOwned {

    Long getRestaurantId();
}
