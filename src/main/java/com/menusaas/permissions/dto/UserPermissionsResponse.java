package com.menusaas.permissions.dto;

import java.util.Set;

/**
 * Permisos efectivos de una persona concreta.
 *
 * @param inherited {@code true} cuando no hay personalización y la persona
 *                  hereda los permisos de su rol.
 * @param permissions conjunto final que la persona tiene hoy.
 */
public record UserPermissionsResponse(
        Long userId,
        String role,
        boolean inherited,
        Set<String> permissions
) {
}
