package com.menusaas.tables.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTableRequest(
        @NotBlank(message = "El número de mesa es obligatorio")
        @Size(max = 20, message = "El número no puede superar 20 caracteres")
        String number,

        @Min(value = 1, message = "Mínimo 1 puesto")
        @Max(value = 20, message = "Máximo 20 puestos")
        Integer seats
) {
}
