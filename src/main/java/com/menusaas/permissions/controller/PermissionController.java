package com.menusaas.permissions.controller;

import com.menusaas.permissions.Permissions;
import com.menusaas.permissions.dto.SetRolePermissionsRequest;
import com.menusaas.permissions.dto.SetUserPermissionsRequest;
import com.menusaas.permissions.dto.UserPermissionsResponse;
import com.menusaas.permissions.service.PermissionService;
import com.menusaas.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Tag(name = "Permissions", description = "Permisos por rol y por persona del restaurante (solo admin)")
@RestController
@RequestMapping("/api/permissions")
@PreAuthorize("hasRole('RESTAURANT_ADMIN')")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @Operation(summary = "Catálogo de permisos disponibles")
    @GetMapping("/catalog")
    public ApiResponse<List<String>> catalog() {
        return ApiResponse.ok(Permissions.ALL);
    }

    @Operation(summary = "Matriz rol -> permisos efectivos del restaurante")
    @GetMapping("/matrix")
    public ApiResponse<Map<String, Set<String>>> matrix() {
        return ApiResponse.ok(permissionService.matrixMine());
    }

    @Operation(summary = "Asignar permisos a un rol (reemplaza el set)")
    @PutMapping
    public ApiResponse<Set<String>> set(@Valid @RequestBody SetRolePermissionsRequest request) {
        return ApiResponse.ok("Permisos actualizados",
                permissionService.setRolePermissions(request.role(), request.permissions()));
    }

    @Operation(summary = "Permisos efectivos de una persona (su personalización o los de su rol)")
    @GetMapping("/user/{userId}")
    public ApiResponse<UserPermissionsResponse> userPermissions(@PathVariable Long userId) {
        return ApiResponse.ok(permissionService.userPermissions(userId));
    }

    @Operation(summary = "Asignar permisos a una persona (reemplaza el set)")
    @PutMapping("/user/{userId}")
    public ApiResponse<UserPermissionsResponse> setUser(@PathVariable Long userId,
                                                        @Valid @RequestBody SetUserPermissionsRequest request) {
        return ApiResponse.ok("Permisos actualizados",
                permissionService.setUserPermissions(userId, request.permissions()));
    }

    @Operation(summary = "Quitar la personalización: la persona vuelve a los permisos de su rol")
    @DeleteMapping("/user/{userId}")
    public ApiResponse<UserPermissionsResponse> clearUser(@PathVariable Long userId) {
        return ApiResponse.ok("Permisos restablecidos al rol",
                permissionService.clearUserPermissions(userId));
    }
}
