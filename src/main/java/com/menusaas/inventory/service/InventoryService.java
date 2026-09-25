package com.menusaas.inventory.service;

import com.menusaas.inventory.dto.IngredientRequest;
import com.menusaas.inventory.dto.IngredientResponse;
import com.menusaas.inventory.dto.StockMovementResponse;
import com.menusaas.inventory.entity.Ingredient;
import com.menusaas.inventory.entity.MovementReason;
import com.menusaas.inventory.entity.RecipeItem;
import com.menusaas.inventory.entity.StockMovement;
import com.menusaas.inventory.repository.IngredientRepository;
import com.menusaas.inventory.repository.RecipeItemRepository;
import com.menusaas.inventory.repository.StockMovementRepository;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kardex + ingredientes. Las recetas (qué lleva cada plato) viven en
 * ProductService; aquí solo existencias y movimientos.
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final StockMovementRepository movementRepository;
    private final IngredientRepository ingredientRepository;
    private final RecipeItemRepository recipeItemRepository;

    /** Registro interno de producto (llamado por ProductService en la misma transacción). */
    public StockMovement record(Long restaurantId, Long productId, int quantity,
                                MovementReason reason, Long orderId) {
        return movementRepository.save(StockMovement.builder()
                .restaurantId(restaurantId)
                .productId(productId)
                .quantity(java.math.BigDecimal.valueOf(quantity))
                .reason(reason)
                .orderId(orderId)
                .build());
    }

    /** Registro interno de ingrediente (cantidades con decimales). */
    public StockMovement recordIngredient(Long restaurantId, Long ingredientId, BigDecimal quantity,
                                          MovementReason reason, Long orderId) {
        return movementRepository.save(StockMovement.builder()
                .restaurantId(restaurantId)
                .ingredientId(ingredientId)
                .quantity(quantity)
                .reason(reason)
                .orderId(orderId)
                .build());
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> listMine(Long productId, Pageable pageable) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        Page<StockMovement> page = (productId != null)
                ? movementRepository.findByRestaurantIdAndProductIdOrderByCreatedAtDesc(restaurantId, productId, pageable)
                : movementRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId, pageable);
        return page.map(StockMovementResponse::from);
    }

    // ------------------------------------------------------------------
    // Ingredientes
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<IngredientResponse> listIngredientsMine() {
        return ingredientRepository.findByRestaurantIdOrderByNameAsc(SecurityUtils.currentRestaurantId())
                .stream()
                .map(IngredientResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IngredientResponse> lowStockIngredientsMine() {
        return ingredientRepository.findByRestaurantIdOrderByNameAsc(SecurityUtils.currentRestaurantId())
                .stream()
                .filter(i -> i.isTrackStock()
                        && i.getStockQuantity().compareTo(i.getLowStockThreshold()) <= 0)
                .map(IngredientResponse::from)
                .toList();
    }

    @Transactional
    public IngredientResponse createIngredientMine(IngredientRequest request) {
        Ingredient ingredient = Ingredient.builder()
                .restaurantId(SecurityUtils.currentRestaurantId())
                .name(request.name().trim())
                .unit(request.unit() != null && !request.unit().isBlank() ? request.unit().trim() : "und")
                .stockQuantity(request.stockQuantity() != null ? request.stockQuantity() : BigDecimal.ZERO)
                .lowStockThreshold(request.lowStockThreshold() != null ? request.lowStockThreshold() : new BigDecimal("5"))
                .unitCost(request.unitCost() != null ? request.unitCost() : BigDecimal.ZERO)
                .trackStock(request.trackStock() == null || request.trackStock())
                .build();
        return IngredientResponse.from(ingredientRepository.save(ingredient));
    }

    @Transactional
    public IngredientResponse updateIngredientMine(Long id, IngredientRequest request) {
        Ingredient ingredient = findIngredientScoped(id);
        ingredient.setName(request.name().trim());
        if (request.unit() != null && !request.unit().isBlank()) ingredient.setUnit(request.unit().trim());
        if (request.stockQuantity() != null) ingredient.setStockQuantity(request.stockQuantity());
        if (request.lowStockThreshold() != null) ingredient.setLowStockThreshold(request.lowStockThreshold());
        if (request.unitCost() != null) ingredient.setUnitCost(request.unitCost());
        if (request.trackStock() != null) ingredient.setTrackStock(request.trackStock());
        return IngredientResponse.from(ingredientRepository.save(ingredient));
    }

    @Transactional
    public void deleteIngredientMine(Long id) {
        ingredientRepository.delete(findIngredientScoped(id));
    }

    @Transactional
    public IngredientResponse adjustIngredientMine(Long id, BigDecimal newQuantity, MovementReason reason) {
        Ingredient ingredient = findIngredientScoped(id);
        BigDecimal delta = newQuantity.subtract(ingredient.getStockQuantity());
        ingredient.setStockQuantity(newQuantity);
        Ingredient saved = ingredientRepository.save(ingredient);
        recordIngredient(saved.getRestaurantId(), id, delta, reason, null);
        return IngredientResponse.from(saved);
    }

    private Ingredient findIngredientScoped(Long id) {
        return ingredientRepository.findByIdAndRestaurantId(id, SecurityUtils.currentRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Ingrediente no encontrado"));
    }

    // ------------------------------------------------------------------
    // Puerta de acceso para products: recetas e ingredientes por tenant.
    // Evita que otros módulos toquen los repositories de inventory.
    // ------------------------------------------------------------------

    /**
     * Ingrediente del tenant o 404 (también sirve para validar pertenencia).
     */
    @Transactional(readOnly = true)
    public Ingredient getIngredientOrThrow(Long ingredientId, Long restaurantId) {
        return ingredientRepository.findByIdAndRestaurantId(ingredientId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Ingrediente no encontrado"));
    }

    /**
     * Ingrediente del tenant o null (para lecturas tolerantes como el editor de recetas).
     */
    @Transactional(readOnly = true)
    public Ingredient findIngredientInRestaurant(Long ingredientId, Long restaurantId) {
        return ingredientRepository.findByIdAndRestaurantId(ingredientId, restaurantId).orElse(null);
    }

    @Transactional
    public Ingredient saveIngredient(Ingredient ingredient) {
        return ingredientRepository.save(ingredient);
    }

    @Transactional(readOnly = true)
    public java.util.List<RecipeItem> findRecipeByProduct(Long productId) {
        return recipeItemRepository.findByProductId(productId);
    }

    @Transactional
    public RecipeItem saveRecipeItem(Long productId, Long ingredientId, java.math.BigDecimal quantity) {
        return recipeItemRepository.save(RecipeItem.builder()
                .productId(productId)
                .ingredientId(ingredientId)
                .quantity(quantity)
                .build());
    }

    @Transactional
    public void deleteRecipeByProduct(Long productId) {
        recipeItemRepository.deleteByProductId(productId);
    }
}
