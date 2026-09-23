package com.menusaas.admin.controller;

import com.menusaas.admin.dto.*;
import com.menusaas.admin.entity.AuditLog;
import com.menusaas.admin.repository.AuditLogRepository;
import com.menusaas.admin.service.AdminRestaurantService;
import com.menusaas.admin.service.AdminStatsService;
import com.menusaas.admin.service.AdminUserService;
import com.menusaas.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin", description = "Gestión global del SaaS (solo SUPER_ADMIN)")
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminStatsService statsService;
    private final AdminRestaurantService restaurantService;
    private final AdminUserService userService;
    private final AuditLogRepository auditLogRepository;

    @Operation(summary = "Métricas globales de la plataforma (cache 30s)")
    @GetMapping("/stats")
    public ApiResponse<AdminStatsResponse> getStats() {
        return ApiResponse.ok(statsService.getStats());
    }

    @Operation(summary = "Listar restaurantes con paginación y búsqueda (?search=&active=&page=&size=)")
    @GetMapping("/restaurants")
    public ApiResponse<Page<AdminRestaurantResponse>> listRestaurants(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ApiResponse.ok(restaurantService.listRestaurants(search, active, pageable));
    }

    @Operation(summary = "Crear un nuevo restaurante y su usuario administrador")
    @PostMapping("/restaurants")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminRestaurantResponse> createRestaurant(@Valid @RequestBody AdminCreateRestaurantRequest request) {
        return ApiResponse.ok("Restaurante creado exitosamente", restaurantService.createRestaurant(request));
    }

    @Operation(summary = "Activar o desactivar restaurante por id")
    @PatchMapping("/restaurants/{id}/active")
    public ApiResponse<Void> toggleRestaurantActive(@PathVariable Long id, @RequestParam boolean active) {
        restaurantService.toggleRestaurantActive(id, active);
        return ApiResponse.ok(active ? "Restaurante activado" : "Restaurante desactivado");
    }

    @Operation(summary = "Listar usuarios con paginación y filtros (?search=&role=&active=&page=&size=)")
    @GetMapping("/users")
    public ApiResponse<Page<AdminUserResponse>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ApiResponse.ok(userService.listUsers(search, role, active, pageable));
    }

    @Operation(summary = "Activar o desactivar un usuario de la plataforma")
    @PatchMapping("/users/{id}/active")
    public ApiResponse<Void> toggleUserActive(@PathVariable Long id, @RequestParam boolean active) {
        userService.toggleUserActive(id, active);
        return ApiResponse.ok(active ? "Usuario activado" : "Usuario desactivado");
    }

    @Operation(summary = "Ver auditoría global o por entidad (?entityType=&entityId=)")
    @GetMapping("/audit")
    public ApiResponse<Page<AuditLog>> listAudit(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @PageableDefault(size = 50, sort = "createdAt") Pageable pageable) {
        if (entityType != null && entityId != null) {
            return ApiResponse.ok(auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, pageable));
        }
        return ApiResponse.ok(auditLogRepository.findAllByOrderByCreatedAtDesc(pageable));
    }
}
