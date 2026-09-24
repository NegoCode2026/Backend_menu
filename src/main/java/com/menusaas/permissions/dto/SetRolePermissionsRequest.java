package com.menusaas.permissions.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SetRolePermissionsRequest(
        @NotNull(message = "El rol es obligatorio")
        String role,

        @NotNull(message = "La lista de permisos es obligatoria")
        List<String> permissions
) {
}
