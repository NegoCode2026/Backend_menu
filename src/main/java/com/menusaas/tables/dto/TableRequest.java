package com.menusaas.tables.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TableRequest(
        @NotBlank(message = "El nombre de la mesa es obligatorio")
        @Size(max = 30, message = "El nombre de la mesa no puede superar 30 caracteres")
        String label
) {
}
