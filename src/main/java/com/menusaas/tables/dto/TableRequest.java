package com.menusaas.tables.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TableRequest(
        @NotBlank(message = "El número de mesa es obligatorio")
        @Size(max = 50, message = "El número de mesa no debe exceder 50 caracteres")
        String number,

        @Min(value = 1, message = "Debe tener al menos 1 asiento")
        @Max(value = 50, message = "No puede superar 50 asientos")
        Integer seats
) {}
