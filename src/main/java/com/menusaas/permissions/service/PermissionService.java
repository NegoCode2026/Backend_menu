package com.menusaas.permissions.service;

import com.menusaas.permissions.Permissions;
import com.menusaas.permissions.dto.UserPermissionsResponse;
import com.menusaas.permissions.entity.RolePermission;
import com.menusaas.permissions.entity.UserPermission;
import com.menusaas.permissions.repository.RolePermissionRepository;
import com.menusaas.permissions.repository.UserPermissionRepository;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.api.ForbiddenException;
import com.menusaas.shared.security.SecurityUtils;
import com.menusaas.users.dto.UserResponse;
import com.menusaas.users.entity.Role;
import com.menusaas.users.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Permisos por rol y por persona, dentro de cada restaurante.
 *
 * Jerarquía de resolución (de mayor a menor):
 * 1. RESTAURANT_ADMIN / SUPER_ADMIN → siempre todo.
 * 2. Filas en {@code user_permissions} → exactamente esos permisos.
 * 3. Filas en {@code role_permissions} → permisos del rol.
 * 4. Sin filas → DEFAULTS del código.
 *
 * El bean se llama "permissions" para usarlo en @PreAuthorize:
 * {@code @PreAuthorize("@permissions.has('MENU_EDIT')")}.
 */
@Service("permissions")
@RequiredArgsConstructor
public class PermissionService {

    private final RolePermissionRepository repository;
    private final UserPermissionRepository userPermissionRepository;
    private final UserService userService;

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

    /** Roles con acceso total, blindados en código (no dependen de BD). */
    private static boolean alwaysAll(String role) {
        return Role.RESTAURANT_ADMIN.equals(role) || Role.SUPER_ADMIN.equals(role);
    }

    private static Set<String> sanitize(List<String> permissions) {
        return permissions == null
                ? Set.of()
                : permissions.stream().filter(Permissions.ALL::contains).collect(Collectors.toSet());
    }

    // ------------------------------------------------------------------
    // Resolución de permisos
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Set<String> effectiveFor(Long restaurantId, String role) {
        if (alwaysAll(role)) {
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

    /**
     * Permisos efectivos de una persona: su personalización individual manda
     * sobre la del rol; si no tiene, hereda los del rol.
     */
    @Transactional(readOnly = true)
    public Set<String> effectiveForUser(Long restaurantId, Long userId, String role) {
        if (alwaysAll(role)) {
            return new HashSet<>(Permissions.ALL);
        }
        List<UserPermission> rows = userPermissionRepository.findByRestaurantIdAndUserId(restaurantId, userId);
        if (rows.isEmpty()) {
            return effectiveFor(restaurantId, role);
        }
        return rows.stream().map(UserPermission::getPermission)
                .filter(p -> !"_NONE".equals(p))
                .collect(Collectors.toSet());
    }

    /** Para @PreAuthorize: true si el usuario actual tiene el permiso. */
    public boolean has(String permission) {
        try {
            var principal = SecurityUtils.currentUser();
            Long restaurantId = principal.getRestaurantId();
            if (restaurantId == null) {
                return Role.SUPER_ADMIN.equals(principal.getRole());
            }
            return effectiveForUser(restaurantId, principal.getId(), principal.getRole())
                    .contains(permission);
        } catch (Exception e) {
            return false;
        }
    }

    /** Para services: lanza 403 si falta el permiso. */
    public void require(String permission) {
        if (!has(permission)) {
            throw new ForbiddenException("No tienes el permiso " + permission);
        }
    }

    // ------------------------------------------------------------------
    // Matriz por rol (solo admin)
    // ------------------------------------------------------------------

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
        Set<String> clean = sanitize(permissions);
        Long restaurantId = SecurityUtils.currentRestaurantId();
        // Por diff (no borrar-todo): evita violaciones del unique ante
        // doble-clic o toggles rápidos que mandan PUTs concurrentes.
        Set<String> current = repository.findByRestaurantIdAndRole(restaurantId, role).stream()
                .map(RolePermission::getPermission)
                .collect(Collectors.toSet());
        for (String permission : clean) {
            if (!current.contains(permission)) {
                try {
                    repository.saveAndFlush(RolePermission.builder()
                            .restaurantId(restaurantId).role(role).permission(permission).build());
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                    // Otro PUT concurrente lo insertó primero: estado final igual.
                }
            }
        }
        for (RolePermission row : repository.findByRestaurantIdAndRole(restaurantId, role)) {
            if (!clean.contains(row.getPermission()) && !"_NONE".equals(row.getPermission())) {
                repository.delete(row);
            }
        }
        // Fila centinela: si queda vacío se guarda una marca para distinguir
        // "sin permisos" de "sin personalizar" (que usaría defaults).
        if (clean.isEmpty()
                && repository.findByRestaurantIdAndRole(restaurantId, role).isEmpty()) {
            repository.save(RolePermission.builder()
                    .restaurantId(restaurantId).role(role).permission("_NONE").build());
        }
        return effectiveFor(restaurantId, role);
    }

    // ------------------------------------------------------------------
    // Permisos por persona (solo admin)
    // ------------------------------------------------------------------

    /** Permisos efectivos de una persona concreta del restaurante. */
    @Transactional(readOnly = true)
    public UserPermissionsResponse userPermissions(Long userId) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        return buildUserPermissions(restaurantId, userService.getMine(userId));
    }

    /**
     * Reemplaza los permisos de una persona. Si queda vacío se guarda la
     * marca {@code _NONE} para no confundir "sin permisos" con "hereda rol".
     */
    @Transactional
    public UserPermissionsResponse setUserPermissions(Long userId, List<String> permissions) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        UserResponse user = userService.getMine(userId);
        if (alwaysAll(user.role())) {
            throw new BadRequestException("El administrador siempre tiene todos los permisos");
        }

        Set<String> clean = sanitize(permissions);
        Set<String> current = userPermissionRepository
                .findByRestaurantIdAndUserId(restaurantId, userId).stream()
                .map(UserPermission::getPermission)
                .collect(Collectors.toSet());

        for (String permission : clean) {
            if (!current.contains(permission)) {
                try {
                    userPermissionRepository.saveAndFlush(UserPermission.builder()
                            .restaurantId(restaurantId).userId(userId).permission(permission).build());
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                    // Otra petición concurrente lo insertó primero: estado final igual.
                }
            }
        }
        for (UserPermission row : userPermissionRepository
                .findByRestaurantIdAndUserId(restaurantId, userId)) {
            if (!clean.contains(row.getPermission()) && !"_NONE".equals(row.getPermission())) {
                userPermissionRepository.delete(row);
            }
        }
        if (clean.isEmpty()
                && userPermissionRepository.findByRestaurantIdAndUserId(restaurantId, userId).isEmpty()) {
            userPermissionRepository.save(UserPermission.builder()
                    .restaurantId(restaurantId).userId(userId).permission("_NONE").build());
        }
        return buildUserPermissions(restaurantId, user);
    }

    /** Quita la personalización: la persona vuelve a los permisos de su rol. */
    @Transactional
    public UserPermissionsResponse clearUserPermissions(Long userId) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        UserResponse user = userService.getMine(userId);
        userPermissionRepository.deleteByRestaurantIdAndUserId(restaurantId, userId);
        return buildUserPermissions(restaurantId, user);
    }

    private UserPermissionsResponse buildUserPermissions(Long restaurantId, UserResponse user) {
        boolean inherited = alwaysAll(user.role())
                || userPermissionRepository.findByRestaurantIdAndUserId(restaurantId, user.id()).isEmpty();
        return new UserPermissionsResponse(
                user.id(), user.role(), inherited,
                effectiveForUser(restaurantId, user.id(), user.role()));
    }
}
