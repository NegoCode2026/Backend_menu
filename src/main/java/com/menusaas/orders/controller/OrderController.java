package com.menusaas.orders.controller;

import com.menusaas.orders.dto.CreateOrderRequest;
import com.menusaas.orders.dto.OrderResponse;
import com.menusaas.orders.dto.OrderStatsResponse;
import com.menusaas.orders.dto.OrderStatusRequest;
import com.menusaas.orders.dto.PayOrderRequest;
import com.menusaas.orders.dto.UpdateOrderRequest;
import com.menusaas.orders.entity.OrderStatus;
import com.menusaas.orders.service.OrderService;
import com.menusaas.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@Tag(name = "Orders", description = "Gestión de pedidos del restaurante (tenant-scoped)")
@RestController
@RequestMapping("/api/orders")
@PreAuthorize("hasAnyRole('RESTAURANT_ADMIN','RESTAURANT_USER','WAITER','CASHIER')")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "Listar pedidos de mi restaurante (filtrable por estado, desde fecha, paginado)")
    @GetMapping
    public ApiResponse<List<OrderResponse>> list(@RequestParam(required = false) OrderStatus status,
                                                 @RequestParam(required = false) Instant since,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(orderService.listMine(status, since, page, size));
    }

    @Operation(summary = "Resumen de pedidos por estado y del día de mi restaurante")
    @GetMapping("/stats")
    public ApiResponse<OrderStatsResponse> stats() {
        return ApiResponse.ok(orderService.statsMine());
    }

    @Operation(summary = "Crear un pedido manualmente (teléfono o presencial)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.ok("Pedido creado exitosamente", orderService.createMine(request));
    }

    @Operation(summary = "Obtener un pedido de mi restaurante por ID")
    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(orderService.getMine(id));
    }

    @Operation(summary = "Editar un pedido (cliente, mesa, notas, ítems)")
    @PatchMapping("/{id}")
    public ApiResponse<OrderResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateOrderRequest request) {
        return ApiResponse.ok("Pedido actualizado", orderService.updateMine(id, request));
    }

    @Operation(summary = "Cambiar el estado de un pedido")
    @PatchMapping("/{id}/status")
    public ApiResponse<OrderResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody OrderStatusRequest request) {
        return ApiResponse.ok("Estado de pedido actualizado", orderService.updateStatusMine(id, request.status()));
    }

    @Operation(summary = "Cobrar un pedido entregado (método de pago)")
    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('RESTAURANT_ADMIN','CASHIER')")
    public ApiResponse<OrderResponse> pay(@PathVariable Long id, @Valid @RequestBody PayOrderRequest request) {
        return ApiResponse.ok("Pedido cobrado", orderService.payMine(id, request.paymentMethod()));
    }

    @Operation(summary = "Enviar notificación de WhatsApp de 'Pedido listo' al cliente")
    @PostMapping("/{id}/notify-whatsapp")
    public ApiResponse<Boolean> notifyWhatsApp(@PathVariable Long id) {
        boolean sent = orderService.notifyWhatsAppMine(id);
        String msg = sent ? "Notificación de WhatsApp enviada al cliente" : "No se pudo enviar la notificación (verifica el teléfono)";
        return ApiResponse.ok(msg, sent);
    }
}