package com.menusaas.permissions.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Permisos a guardar para una persona. Lista vacía = sin permisos
 * (no equivale a heredar del rol: eso es DELETE).
 */
public record SetUserPermissionsRequest(
        @NotNull(message = "La lista de permisos es obligatoria")
        List<String> permissions
) {
}
