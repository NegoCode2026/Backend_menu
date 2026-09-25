package com.menusaas.inventory.dto;

import com.menusaas.inventory.entity.RecipeItem;

import java.math.BigDecimal;

public record RecipeItemResponse(
        Long id,
        Long ingredientId,
        String ingredientName,
        String unit,
        BigDecimal quantity
) {
    public static RecipeItemResponse from(RecipeItem line, String ingredientName, String unit) {
        return new RecipeItemResponse(
                line.getId(), line.getIngredientId(), ingredientName, unit, line.getQuantity());
    }
}
