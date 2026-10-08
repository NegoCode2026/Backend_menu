package com.menusaas.modifiers.service;

import com.menusaas.modifiers.entity.*;
import com.menusaas.modifiers.repository.*;

import java.util.Collection;
import java.util.Map;
import com.menusaas.shared.api.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resuelve y valida las opciones que eligió el cliente.
 *
 * <p><b>El precio sale de aquí, nunca del cliente.</b> El pedido llega con
 * identificadores de opción; el delta se lee de la tabla. Si se aceptara el
 * precio desde el payload, cualquiera con un curl podría decidir lo que paga.
 *
 * <p>Valida además el contrato de cada grupo (obligatorio, mínimo, máximo) y que
 * las opciones pertenezcan a grupos que el producto realmente ofrece, para que no
 * se puedan pedir opciones de otro producto.
 */
@Service
@RequiredArgsConstructor
public class ModifierSelectionService {

    private final ModifierGroupRepository groupRepository;
    private final ModifierRepository modifierRepository;
    private final ProductModifierGroupRepository productGroupRepository;
    private final OrderItemModifierRepository orderItemModifierRepository;

    /** Una opción ya resuelta, con el precio congelado de la tabla. */
    public record ResolvedModifier(String groupName, String modifierName,
                                   java.math.BigDecimal priceDelta, int quantity) {
        public java.math.BigDecimal total() {
            return priceDelta.multiply(java.math.BigDecimal.valueOf(quantity));
        }
    }

    /**
     * @param selectedIds ids de opción que eligió el cliente (con cantidad)
     * @return deltas resueltos y congelados
     * @throws BadRequestException si no cumple el contrato del grupo o la opción
     *                            no pertenece al producto
     */
    @Transactional(readOnly = true)
    public List<ResolvedModifier> resolve(Long productId, Long restaurantId,
                                         Map<Long, Integer> selectedIds) {
        if (selectedIds == null || selectedIds.isEmpty()) {
            validateRequiredGroupsNothingSelected(productId, restaurantId);
            return List.of();
        }

        List<ProductModifierGroup> offered = productGroupRepository
                .findByRestaurantIdAndProductIdOrderByPositionAsc(restaurantId, productId);
        Map<Long, ProductModifierGroup> offeredByGroup = offered.stream()
                .collect(Collectors.toMap(ProductModifierGroup::getGroupId, Function.identity()));

        // Todas las opciones elegidas deben existir y ser de un grupo del producto.
        Map<Long, Modifier> byId = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> entry : selectedIds.entrySet()) {
            Modifier modifier = modifierRepository.findById(entry.getKey())
                    .orElseThrow(() -> new BadRequestException("Opción no válida: ID " + entry.getKey()));
            if (!modifier.getRestaurantId().equals(restaurantId)) {
                throw new BadRequestException("Opción no válida para este restaurante");
            }
            if (!offeredByGroup.containsKey(modifier.getGroupId())) {
                throw new BadRequestException(
                        "La opción '" + modifier.getName() + "' no pertenece a este producto");
            }
            if (!modifier.isActive()) {
                throw new BadRequestException("La opción '" + modifier.getName() + "' ya no está disponible");
            }
            byId.put(entry.getKey(), modifier);
        }

        // Contrato de cada grupo: obligatorio / min / max.
        Map<Long, List<Modifier>> byGroup = byId.values().stream()
                .collect(Collectors.groupingBy(Modifier::getGroupId));

        for (ProductModifierGroup link : offered) {
            ModifierGroup group = groupRepository.findById(link.getGroupId()).orElse(null);
            if (group == null || !group.isActive()) {
                continue;
            }
            int chosen = byGroup.getOrDefault(group.getId(), List.of()).size();
            int totalUnits = byGroup.getOrDefault(group.getId(), List.of()).stream()
                    .mapToInt(m -> selectedIds.getOrDefault(m.getId(), 1)).sum();

            if (group.isRequired() && chosen == 0) {
                throw new BadRequestException(
                        "Debes elegir una opción de '" + group.getName() + "'");
            }
            if (chosen < group.getMinSelections()) {
                throw new BadRequestException("Debes elegir al menos " + group.getMinSelections()
                        + " opciones de '" + group.getName() + "'");
            }
            if (chosen > group.getMaxSelections()) {
                throw new BadRequestException("No puedes elegir más de " + group.getMaxSelections()
                        + " opciones de '" + group.getName() + "'");
            }
            if (totalUnits > group.getMaxSelections()) {
                throw new BadRequestException("No puedes pedir más de " + group.getMaxSelections()
                        + " unidades por opción de '" + group.getName() + "'");
            }
        }

        List<ResolvedModifier> out = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : selectedIds.entrySet()) {
            Modifier modifier = byId.get(entry.getKey());
            ModifierGroup group = groupRepository.findById(modifier.getGroupId())
                    .orElseThrow(() -> new BadRequestException("Grupo de opción no encontrado"));
            int quantity = entry.getValue() == null || entry.getValue() < 1 ? 1 : entry.getValue();
            out.add(new ResolvedModifier(group.getName(), modifier.getName(),
                    modifier.getPriceDelta(), quantity));
        }
        return out;
    }

    private void validateRequiredGroupsNothingSelected(Long productId, Long restaurantId) {
        List<ProductModifierGroup> offered = productGroupRepository
                .findByRestaurantIdAndProductIdOrderByPositionAsc(restaurantId, productId);
        for (ProductModifierGroup link : offered) {
            ModifierGroup group = groupRepository.findById(link.getGroupId()).orElse(null);
            if (group != null && group.isActive() && group.isRequired()) {
                throw new BadRequestException("Debes elegir una opción de '" + group.getName() + "'");
            }
        }
    }

    /** Opciones de unos items, agrupadas por id de item (batch, sin N+1). */
    @Transactional(readOnly = true)
    public Map<Long, List<OrderItemModifier>> byOrderItemIds(Collection<Long> orderItemIds) {
        if (orderItemIds == null || orderItemIds.isEmpty()) {
            return Map.of();
        }
        return orderItemModifierRepository.findByOrderItemIdIn(orderItemIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(OrderItemModifier::getOrderItemId));
    }

    /** Guarda las opciones de los items de un pedido, en un lote. */
    @Transactional
    public void persistAll(List<OrderItemModifier> rows) {
        if (rows != null && !rows.isEmpty()) {
            orderItemModifierRepository.saveAll(rows);
        }
    }

}
