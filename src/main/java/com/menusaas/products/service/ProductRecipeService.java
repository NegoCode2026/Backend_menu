package com.menusaas.products.service;

import com.menusaas.inventory.dto.RecipeItemResponse;
import com.menusaas.inventory.dto.RecipeLineRequest;
import com.menusaas.inventory.entity.Ingredient;
import com.menusaas.inventory.entity.MovementReason;
import com.menusaas.inventory.entity.RecipeItem;
import com.menusaas.inventory.service.InventoryService;
import com.menusaas.products.entity.Product;
import com.menusaas.products.repository.ProductRepository;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Recetas y existencias por pedido: el plato descuenta ingredientes (con
 * receta) o stock propio (sin receta). Todo con tenant explícito.
 *
 * ProductService delega estos casos de uso sin cambiar su API pública.
 */
@Service
@RequiredArgsConstructor
public class ProductRecipeService {

    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    /**
     * Reemplaza la receta del plato. Cada ingrediente debe ser del tenant.
     * Lista vacía = quitar receta (vuelve a stock propio).
     */
    @Transactional
    public List<RecipeItem> setRecipeMine(Long productId, List<RecipeLineRequest> lines) {
        Product product = findScoped(productId);
        inventoryService.deleteRecipeByProduct(productId);
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<RecipeItem> saved = new ArrayList<>();
        for (RecipeLineRequest line : lines) {
            if (line.quantity() == null || line.quantity().signum() <= 0) {
                throw new BadRequestException("La cantidad del ingrediente debe ser mayor a cero");
            }
            Ingredient ingredient = inventoryService.getIngredientOrThrow(
                    line.ingredientId(), product.getRestaurantId());
            saved.add(inventoryService.saveRecipeItem(productId, ingredient.getId(), line.quantity()));
        }
        return saved;
    }

    /**
     * ¿Se puede vender esta cantidad? Con receta: todos los ingredientes
     * alcanzan; sin receta: solo el flag disponible (el stock propio se
     * valida al descontar).
     */
    @Transactional(readOnly = true)
    public boolean canFulfill(Long productId, Long restaurantId, int quantity) {
        Product product = findOrThrow(productId, restaurantId);
        if (!product.isAvailable()) {
            return false;
        }
        List<RecipeItem> recipe = inventoryService.findRecipeByProduct(productId);
        if (recipe.isEmpty()) {
            return true;
        }
        for (RecipeItem line : recipe) {
            Ingredient ingredient = inventoryService.findIngredientInRestaurant(
                    line.getIngredientId(), restaurantId);
            if (ingredient == null || !ingredient.isTrackStock()) {
                continue;
            }
            java.math.BigDecimal need = line.getQuantity()
                    .multiply(java.math.BigDecimal.valueOf(quantity));
            if (ingredient.getStockQuantity().compareTo(need) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Descuento por pedido: con receta descuenta ingredientes, sin receta
     * usa el stock propio del producto.
     */
    @Transactional
    public void deductForOrder(Long productId, Long restaurantId, int quantity, Long orderId) {
        List<RecipeItem> recipe = inventoryService.findRecipeByProduct(productId);
        if (!recipe.isEmpty()) {
            for (RecipeItem line : recipe) {
                Ingredient ingredient = inventoryService.getIngredientOrThrow(
                        line.getIngredientId(), restaurantId);
                if (!ingredient.isTrackStock()) {
                    continue;
                }
                java.math.BigDecimal need = line.getQuantity()
                        .multiply(java.math.BigDecimal.valueOf(quantity));
                if (ingredient.getStockQuantity().compareTo(need) < 0) {
                    throw new BadRequestException("Sin ingredientes suficientes para '"
                            + ingredient.getName() + "' (falta " + need.stripTrailingZeros().toPlainString()
                            + " " + ingredient.getUnit() + ")");
                }
                ingredient.setStockQuantity(ingredient.getStockQuantity().subtract(need));
                inventoryService.saveIngredient(ingredient);
                inventoryService.recordIngredient(restaurantId, ingredient.getId(),
                        need.negate(), MovementReason.ORDER, orderId);
            }
            return;
        }
        Product product = findOrThrow(productId, restaurantId);
        if (product.isTrackStock()) {
            deductStock(product.getId(), restaurantId, quantity, orderId);
        }
    }

    /** Devolución al cancelar: espejo de deductForOrder. */
    @Transactional
    public void restoreForOrder(Long productId, Long restaurantId, int quantity, Long orderId) {
        List<RecipeItem> recipe = inventoryService.findRecipeByProduct(productId);
        if (!recipe.isEmpty()) {
            for (RecipeItem line : recipe) {
                Ingredient ingredient = inventoryService.findIngredientInRestaurant(
                        line.getIngredientId(), restaurantId);
                if (ingredient == null || !ingredient.isTrackStock()) {
                    continue;
                }
                java.math.BigDecimal back = line.getQuantity()
                        .multiply(java.math.BigDecimal.valueOf(quantity));
                ingredient.setStockQuantity(ingredient.getStockQuantity().add(back));
                inventoryService.saveIngredient(ingredient);
                inventoryService.recordIngredient(restaurantId, ingredient.getId(),
                        back, MovementReason.CANCEL_RESTORE, orderId);
            }
            return;
        }
        Product product = findOrThrow(productId, restaurantId);
        if (product.isTrackStock()) {
            restoreStock(product.getId(), restaurantId, quantity, orderId);
        }
    }

    /**
     * Descuenta existencias por un pedido. Si el producto no rastrea stock
     * no hace nada. Lanza 400 si no hay suficientes existencias.
     */
    @Transactional
    public Product deductStock(Long productId, Long restaurantId, int quantity, Long orderId) {
        Product product = findOrThrow(productId, restaurantId);
        if (!product.isTrackStock()) {
            return product;
        }
        if (product.getStockQuantity() < quantity) {
            throw new BadRequestException("Stock insuficiente para '" + product.getName()
                    + "' (disponible: " + product.getStockQuantity() + ")");
        }
        product.setStockQuantity(product.getStockQuantity() - quantity);
        Product saved = productRepository.save(product);
        inventoryService.record(restaurantId, productId, -quantity, MovementReason.ORDER, orderId);
        return saved;
    }

    /** Devuelve existencias al cancelar un pedido (solo si rastrea stock). */
    @Transactional
    public void restoreStock(Long productId, Long restaurantId, int quantity, Long orderId) {
        Product product = findOrThrow(productId, restaurantId);
        if (!product.isTrackStock()) {
            return;
        }
        product.setStockQuantity(product.getStockQuantity() + quantity);
        productRepository.save(product);
        inventoryService.record(restaurantId, productId, quantity, MovementReason.CANCEL_RESTORE, orderId);
    }

    /** Receta del plato con nombres (para el editor). */
    @Transactional(readOnly = true)
    public List<RecipeItemResponse> getRecipeMine(Long productId) {
        Product product = findScoped(productId);
        List<RecipeItem> lines = inventoryService.findRecipeByProduct(productId);
        List<RecipeItemResponse> out = new ArrayList<>();
        for (RecipeItem line : lines) {
            Ingredient ingredient = inventoryService.findIngredientInRestaurant(
                    line.getIngredientId(), product.getRestaurantId());
            out.add(RecipeItemResponse.from(line,
                    ingredient != null ? ingredient.getName() : "Ingrediente #" + line.getIngredientId(),
                    ingredient != null ? ingredient.getUnit() : "und"));
        }
        return out;
    }

    private Product findScoped(Long id) {
        return findOrThrow(id, SecurityUtils.currentRestaurantId());
    }

    private Product findOrThrow(Long id, Long restaurantId) {
        return productRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
    }
}
