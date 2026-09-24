package com.menusaas.admin.service;

import com.menusaas.admin.dto.AdminUserResponse;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.api.ForbiddenException;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
import com.menusaas.users.entity.Role;
import com.menusaas.users.entity.User;
import com.menusaas.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestión de usuarios a nivel plataforma (SUPER_ADMIN).
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(String search, String role, Boolean active, Pageable pageable) {
        String normalizedRole = (role == null || role.isBlank() || "all".equalsIgnoreCase(role)) ? null : role.trim();
        if (normalizedRole != null && normalizedRole.startsWith("ROLE_")) {
            normalizedRole = normalizedRole.substring(5);
        }
        if (normalizedRole != null && !List.of(Role.SUPER_ADMIN, Role.RESTAURANT_ADMIN, Role.RESTAURANT_USER, Role.WAITER, Role.CASHIER).contains(normalizedRole)) {
            throw new BadRequestException("Rol inválido: " + role);
        }
        final String roleFilter = normalizedRole;
        Page<User> page = userRepository.search(search, roleFilter, active, pageable);
        return page.map(AdminUserResponse::from);
    }

    /** Compat con callers antiguos. */
    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        return listUsers(null, null, null, Pageable.ofSize(200)).getContent();
    }

    @CacheEvict(value = "adminStats", allEntries = true)
    @Transactional
    public void toggleUserActive(Long id, boolean active) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (user.getId().equals(SecurityUtils.currentUser().getId()) && !active) {
            throw new ForbiddenException("No puede desactivarse a sí mismo");
        }

        if (Role.SUPER_ADMIN.equals(user.getRole().getName()) && !active
                && userRepository.countOtherActiveSuperAdmins(user.getId()) == 0) {
            throw new ForbiddenException("No se puede desactivar al último Super Admin activo");
        }

        user.setActive(active);
        userRepository.save(user);
        auditService.log(active ? "USER_ACTIVATED" : "USER_DEACTIVATED",
                "user", id, "email=" + user.getEmail() + ", active=" + active);
    }
}
