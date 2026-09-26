package com.menusaas.tables.dto;

import java.util.UUID;

/**
 * Datos que el frontend necesita para dibujar el QR de una mesa.
 * El backend NO genera la imagen: solo entrega code + menuUrl.
 */
public record TableQrInfoResponse(
        Long tableId,
        String label,
        UUID code,
        String restaurantSlug,
        String menuUrl
) {
}
