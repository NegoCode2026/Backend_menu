package com.menusaas.orders.service;

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
import java.util.List;

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

    private final ProductService productService;

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

            BigDecimal unitPrice = product.getPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.quantity()));
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
