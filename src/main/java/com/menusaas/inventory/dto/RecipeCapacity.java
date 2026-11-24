package com.menusaas.inventory.dto;

import java.math.BigDecimal;

/** A dish and one of its ingredients. A null ingredient indicates a dish without a recipe. */
public interface RecipeCapacity {
    Long getProductId();
    String getProductName();
    Long getIngredientId();
    String getIngredientName();
    String getUnit();
    BigDecimal getStock();
    BigDecimal getThreshold();
    BigDecimal getPerServing();
    BigDecimal getServings();
    Boolean getTracked();
}
