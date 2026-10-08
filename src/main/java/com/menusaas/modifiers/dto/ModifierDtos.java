package com.menusaas.modifiers.dto;

import com.menusaas.modifiers.entity.Modifier;
import com.menusaas.modifiers.entity.ModifierGroup;

import java.math.BigDecimal;
import java.util.List;

/** Payloads de grupos de opciones y sus opciones. */
public final class ModifierDtos {

    private ModifierDtos() {
    }

    public record GroupRequest(
            String name,
            String description,
            /** Mínimo de opciones elegibles. 0 = opcional. */
            Integer minSelections,
            Integer maxSelections,
            Boolean required,
            Integer position
    ) {
    }

    public record OptionRequest(
            String name,
            /** Delta sobre el precio del producto. Puede ser 0 o negativo. */
            BigDecimal priceDelta,
            Integer position
    ) {
    }

    public record OptionResponse(Long id, Long groupId, String name,
                                 BigDecimal priceDelta, boolean active) {
        public static OptionResponse from(Modifier m) {
            return new OptionResponse(m.getId(), m.getGroupId(), m.getName(),
                    m.getPriceDelta(), m.isActive());
        }
    }

    /** Grupo con sus opciones: lo que necesita el cliente para elegir. */
    public record GroupResponse(
            Long id, String name, String description,
            int minSelections, int maxSelections, boolean required, int position,
            List<OptionResponse> options
    ) {
        public static GroupResponse from(ModifierGroup g, List<Modifier> options) {
            return new GroupResponse(g.getId(), g.getName(), g.getDescription(),
                    g.getMinSelections(), g.getMaxSelections(), g.isRequired(), g.getPosition(),
                    options.stream().map(OptionResponse::from).toList());
        }
    }
}
