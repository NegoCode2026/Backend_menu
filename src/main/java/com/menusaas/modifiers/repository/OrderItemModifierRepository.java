package com.menusaas.modifiers.repository;

import com.menusaas.modifiers.entity.OrderItemModifier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OrderItemModifierRepository extends JpaRepository<OrderItemModifier, Long> {

    List<OrderItemModifier> findByOrderItemId(Long orderItemId);

    /** Batch del historial: evita N+1 al mapear los pedidos con sus opciones. */
    List<OrderItemModifier> findByOrderItemIdIn(Collection<Long> orderItemIds);

    void deleteByOrderItemId(Long orderItemId);
}
