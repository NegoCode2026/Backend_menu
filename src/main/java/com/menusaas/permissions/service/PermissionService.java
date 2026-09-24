package com.menusaas.permissions.service;

import com.menusaas.permissions.Permissions;
import com.menusaas.permissions.entity.RolePermission;
import com.menusaas.permissions.repository.RolePermissionRepository;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.api.ForbiddenException;
import com.menusaas.shared.security.SecurityUtils;
import com.menusaas.users.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Permisos por rol y restaurante. RESTAURANT_ADMIN siempre tiene todo.
 * Sin filas personalizadas valen los DEFAULTS; al guardar por primera vez
 * se parte de los defaults y se edita libremente.
 *
 * El bean se llama "permissions" para usarlo en @PreAuthorize:
 * {@code @PreAuthorize("@permissions.has('MENU_EDIT')")}.
 */
@Service("permissions")
@RequiredArgsConstructor
public class PermissionService {

    private final RolePermissionRepository repository;

    private static final Map<String, Set<String>> DEFAULTS = Map.of(
            Role.RESTAURANT_USER, Set.of(
                    Permissions.ORDER_SERVE, Permissions.ORDER_KITCHEN, Permissions.ORDERS_EDIT),
            Role.WAITER, Set.of(
                    Permissions.ORDER_SERVE),
            Role.CASHIER, Set.of(
                    Permissions.ORDER_SERVE, Permissions.ORDER_CANCEL,
                    Permissions.CASH_CHARGE, Permissions.CASH_CLOSE, Permissions.ORDERS_EDIT));

    /** Roles configurables (el admin no se toca). */
    public static final List<String> MANAGEABLE_ROLES =
            List.of(Role.RESTAURANT_USER, Role.WAITER, Role.CASHIER);

    @Transactional(readOnly = true)
    public Set<String> effectiveFor(Long restaurantId, String role) {
        if (Role.RESTAURANT_ADMIN.equals(role) || Role.SUPER_ADMIN.equals(role)) {
            return new HashSet<>(Permissions.ALL);
        }
        List<RolePermission> rows = repository.findByRestaurantIdAndRole(restaurantId, role);
        if (rows.isEmpty()) {
            return new HashSet<>(DEFAULTS.getOrDefault(role, Set.of()));
        }
        return rows.stream().map(RolePermission::getPermission)
                .filter(p -> !"_NONE".equals(p))
                .collect(Collectors.toSet());
    }

    /** Para @PreAuthorize: true si el usuario actual tiene el permiso. */
    public boolean has(String permission) {
        try {
            String role = SecurityUtils.currentUser().getRole();
            Long restaurantId = SecurityUtils.currentUser().getRestaurantId();
            if (restaurantId == null) {
                return Role.SUPER_ADMIN.equals(role);
            }
            return effectiveFor(restaurantId, role).contains(permission);
        } catch (Exception e) {
            return false;
        }
    }

    /** Para services: lanza 403 si falta el permiso. */
    public void require(String permission) {
        if (!has(permission)) {
            throw new ForbiddenException("Tu rol no tiene el permiso " + permission);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Set<String>> matrixMine() {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        Map<String, Set<String>> matrix = new java.util.LinkedHashMap<>();
        for (String role : MANAGEABLE_ROLES) {
            matrix.put(role, effectiveFor(restaurantId, role));
        }
        return matrix;
    }

    @Transactional
    public Set<String> setRolePermissions(String role, List<String> permissions) {
        if (!MANAGEABLE_ROLES.contains(role)) {
            throw new BadRequestException("Rol no configurable: " + role);
        }
        Set<String> clean = permissions == null ? Set.of() : permissions.stream()
                .filter(Permissions.ALL::contains)
                .collect(Collectors.toSet());
        Long restaurantId = SecurityUtils.currentRestaurantId();
        repository.deleteByRestaurantIdAndRole(restaurantId, role);
        // Fila centinela: si queda vacío se guarda una marca para distinguir
        // "sin permisos" de "sin personalizar" (que usaría defaults).
        if (clean.isEmpty()) {
            repository.save(RolePermission.builder()
                    .restaurantId(restaurantId).role(role).permission("_NONE").build());
        } else {
            for (String permission : clean) {
                repository.save(RolePermission.builder()
                        .restaurantId(restaurantId).role(role).permission(permission).build());
            }
        }
        return effectiveFor(restaurantId, role);
    }
}
