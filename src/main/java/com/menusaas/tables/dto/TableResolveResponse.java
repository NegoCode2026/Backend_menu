package com.menusaas.tables.dto;

/**
 * Respuesta pública al validar un ?t={code}: dice la mesa real y el
 * restaurante al que pertenece, para que el front auto-rellene el pedido.
 */
public record TableResolveResponse(
        Long tableId,
        String tableLabel,
        Long restaurantId,
        String restaurantSlug,
        String restaurantName,
        String menuUrl
) {
}
