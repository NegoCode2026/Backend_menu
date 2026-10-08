package com.menusaas.orders.service;

import com.menusaas.modifiers.entity.OrderItemModifier;
import com.menusaas.modifiers.service.ModifierSelectionService;
import com.menusaas.orders.dto.OrderItemRequest;
import com.menusaas.orders.entity.Order;
import com.menusaas.orders.entity.OrderItem;
import com.menusaas.products.entity.Product;
import com.menusaas.products.service.ProductService;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.api.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Valida productos del tenant, calcula totales y congela el costo unitario
 * (snapshot para utilidades históricas).
 * Total = subtotal ítems - descuento (tope: subtotal) + propina.
 *
 * Misma transacción del llamador (no abre una propia).
 */
@Service
@RequiredArgsConstructor
public class OrderPricing {

    /** Opciones por item, pendientes de persistir cuando se conozca su id. */
    private final Map<OrderItem, List<OrderItemModifier>> pendingModifiers = new LinkedHashMap<>();

    /** Lo guarda OrderService tras persistir los items del pedido. */
    public Map<OrderItem, List<OrderItemModifier>> pendingModifiers() {
        return pendingModifiers;
    }


    private final ProductService productService;
    private final ModifierSelectionService modifierSelection;

    public void applyItems(Order order, Long restaurantId, List<OrderItemRequest> itemRequests,
                           BigDecimal discountAmount, BigDecimal tipAmount) {
        order.getItems().clear();
        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : itemRequests) {
            final Product product;
            try {
                product = productService.getByIdAndRestaurantIdOrThrow(itemReq.productId(), restaurantId);
            } catch (ResourceNotFoundException e) {
                // Contrato público: producto ajeno/inexistente es 400, no 404
                // (el pedido aún no existe; es error del payload).
                throw new BadRequestException("Producto no disponible en el menú: ID " + itemReq.productId());
            }
            if (!product.isAvailable()) {
                throw new BadRequestException("El producto '" + product.getName() + "' no se encuentra disponible actualmente");
            }
            // Defensa extra: el producto debe pertenecer al tenant del pedido.
            if (!restaurantId.equals(product.getRestaurantId())) {
                throw new BadRequestException("Producto no disponible en el menú: ID " + itemReq.productId());
            }
            // Con receta se validan ingredientes; sin receta, el stock propio.
            if (!productService.canFulfill(product.getId(), restaurantId, itemReq.quantity())) {
                throw new BadRequestException("Sin existencias suficientes para '" + product.getName() + "'");
            }

            // Opciones (tamaño, término, extras): el delta se lee de la tabla,
            // nunca del payload. La suma por unidad entra en el subtotal del item,
            // de modo que las utilidades siguen teniendo en cuenta lo que se cobró.
            Map<Long, Integer> selected = new LinkedHashMap<>();
            if (itemReq.modifiers() != null) {
                for (OrderItemRequest.SelectedModifier sel : itemReq.modifiers()) {
                    if (sel.modifierId() == null) {
                        continue;
                    }
                    selected.merge(sel.modifierId(),
                            sel.quantity() == null ? 1 : sel.quantity(), Integer::sum);
                }
            }
            List<ModifierSelectionService.ResolvedModifier> resolved =
                    modifierSelection.resolve(product.getId(), restaurantId, selected);

            BigDecimal modifiersUnitTotal = resolved.stream()
                    .map(ModifierSelectionService.ResolvedModifier::total)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // El precio unitario sigue siendo el del producto: el desglose de
            // opciones va aparte, guardado con precio congelado en el item.
            BigDecimal unitPrice = product.getPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.quantity()))
                    .add(modifiersUnitTotal);
            total = total.add(subtotal);

            OrderItem item = OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .unitPrice(unitPrice)
                    .unitCost(product.getCostPrice() != null
                            ? product.getCostPrice() : BigDecimal.ZERO)
                    .quantity(itemReq.quantity())
                    .subtotal(subtotal)
                    .notes(itemReq.notes() != null ? itemReq.notes().trim() : null)
                    .build();

            order.addItem(item);

            // Las opciones quedan en una lista aparte para persistirlas después
            // de conocer el id del item.
            pendingModifiers.put(item, resolved.stream()
                    .map(r -> OrderItemModifier.builder()
                            .groupName(r.groupName())
                            .modifierName(r.modifierName())
                            .priceDelta(r.priceDelta())
                            .quantity(r.quantity())
                            .restaurantId(restaurantId)
                            .build())
                    .toList());
        }

        BigDecimal subtotal = total;
        BigDecimal discount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("El descuento no puede ser negativo");
        }
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }
        BigDecimal tip = tipAmount != null ? tipAmount : BigDecimal.ZERO;
        if (tip.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("La propina no puede ser negativa");
        }
        order.setDiscountAmount(discount);
        order.setTipAmount(tip);
        order.setTotalAmount(subtotal.subtract(discount).add(tip));
    }
}
