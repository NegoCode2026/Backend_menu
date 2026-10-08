package com.menusaas.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrderItemRequest(
        @NotNull(message = "El id del producto es obligatorio")
        Long productId,

        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad debe ser al menos 1")
        Integer quantity,

        @Size(max = 255, message = "Las notas del producto no pueden superar 255 caracteres")
        String notes,

        /**
         * Opciones que eligió el cliente (tamaño, término, extras).
         *
         * <p>Solo se envían los identificadores. El precio se calcula en servidor
         * a partir de la tabla de modifiers: aceptar un delta desde el cliente
         * sería dejar que cualquiera con un curl decida lo que paga.
         */
        @Valid
        List<SelectedModifier> modifiers
) {

    public record SelectedModifier(
            @NotNull(message = "El id de la opción es obligatorio")
            Long modifierId,

            @Min(value = 1, message = "La cantidad debe ser al menos 1")
            Integer quantity
    ) {
    }
}
