package com.menusaas.inventory.service;

import com.menusaas.inventory.dto.StockMovementResponse;
import com.menusaas.inventory.entity.MovementReason;
import com.menusaas.inventory.entity.StockMovement;
import com.menusaas.inventory.repository.StockMovementRepository;
import com.menusaas.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kardex: solo registra y consulta movimientos. Las mutaciones de
 * existencias viven en ProductService (dueño de la columna stock).
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final StockMovementRepository movementRepository;

    /** Registro interno (llamado por ProductService en la misma transacción). */
    public StockMovement record(Long restaurantId, Long productId, int quantity,
                                MovementReason reason, Long orderId) {
        return movementRepository.save(StockMovement.builder()
                .restaurantId(restaurantId)
                .productId(productId)
                .quantity(quantity)
                .reason(reason)
                .orderId(orderId)
                .build());
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> listMine(Long productId, Pageable pageable) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        Page<StockMovement> page = (productId != null)
                ? movementRepository.findByRestaurantIdAndProductIdOrderByCreatedAtDesc(restaurantId, productId, pageable)
                : movementRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId, pageable);
        return page.map(StockMovementResponse::from);
    }
}
