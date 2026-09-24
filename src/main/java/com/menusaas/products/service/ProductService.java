package com.menusaas.products.service;

import com.menusaas.categories.service.CategoryService;
import com.menusaas.inventory.entity.Ingredient;
import com.menusaas.inventory.entity.MovementReason;
import com.menusaas.inventory.entity.RecipeItem;
import com.menusaas.inventory.service.InventoryService;
import com.menusaas.shared.security.SignedUrlService;
import com.menusaas.products.dto.ProductRequest;
import com.menusaas.products.dto.ProductResponse;
import com.menusaas.products.entity.Product;
import com.menusaas.products.repository.ProductRepository;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final SignedUrlService signedUrlService;
    private final InventoryService inventoryService;

    @Transactional(readOnly = true)
    public Page<ProductResponse> listMine(Long categoryId, Pageable pageable) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        Page<Product> products = categoryId != null
                ? productRepository.findByCategoryIdAndRestaurantIdOrderByPositionAsc(categoryId, restaurantId, pageable)
                : productRepository.findByRestaurantIdOrderByPositionAsc(restaurantId, pageable);
        return products.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse getMine(Long id) {
        return toResponse(findScoped(id));
    }

    @Transactional
    public ProductResponse createMine(ProductRequest request) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        validateCategoryBelongsToTenant(request.categoryId(), restaurantId);

        Product product = Product.builder()
                .restaurantId(restaurantId)
                .categoryId(request.categoryId())
                .name(request.name().trim())
                .description(request.description())
                .price(request.price())
                .imageUrl(signedUrlService.toStoredValue(request.imageUrl()))
                .available(request.available() == null || request.available())
                .position(request.position() != null ? request.position() : 0)
                .costPrice(request.costPrice() != null ? request.costPrice() : java.math.BigDecimal.ZERO)
                .stockQuantity(request.stockQuantity() != null ? request.stockQuantity() : 0)
                .lowStockThreshold(request.lowStockThreshold() != null ? request.lowStockThreshold() : 5)
                .trackStock(request.trackStock() != null && request.trackStock())
                .build();
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse updateMine(Long id, ProductRequest request) {
        Product product = findScoped(id);
        validateCategoryBelongsToTenant(request.categoryId(), product.getRestaurantId());

        product.setCategoryId(request.categoryId());
        product.setName(request.name().trim());
        if (request.description() != null) product.setDescription(request.description());
        product.setPrice(request.price());
        if (request.imageUrl() != null) product.setImageUrl(signedUrlService.toStoredValue(request.imageUrl()));
        if (request.available() != null) product.setAvailable(request.available());
        if (request.position() != null) product.setPosition(request.position());
        if (request.costPrice() != null) product.setCostPrice(request.costPrice());
        if (request.stockQuantity() != null) product.setStockQuantity(request.stockQuantity());
        if (request.lowStockThreshold() != null) product.setLowStockThreshold(request.lowStockThreshold());
        if (request.trackStock() != null) product.setTrackStock(request.trackStock());
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public void deleteMine(Long id) {
        Product product = findScoped(id);
        productRepository.delete(product);
    }

    // ------------------------------------------------------------------
    // Consultas explícitas por tenant (sin SecurityUtils).
    // Puerta de acceso para orders/publicmenu/categories: evita que otros
    // módulos toquen ProductRepository directamente.
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Product getByIdAndRestaurantIdOrThrow(Long id, Long restaurantId) {
        return productRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
    }

    @Transactional(readOnly = true)
    public java.util.List<Product> findAvailableByCategory(Long categoryId, Long restaurantId) {
        return productRepository.findByCategoryScoped(categoryId, restaurantId, true);
    }

    @Transactional(readOnly = true)
    public java.util.List<Product> findUncategorized(Long restaurantId) {
        return productRepository.findByRestaurantIdAndCategoryIdIsNullOrderByPositionAsc(restaurantId);
    }

    @Transactional(readOnly = true)
    public java.util.Map<Long, Long> countAvailableGroupedByRestaurant() {
        java.util.Map<Long, Long> counts = new java.util.HashMap<>();
        for (Object[] row : productRepository.countAvailableGroupedByRestaurant()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public long countByRestaurant(Long restaurantId) {
        return productRepository.countByRestaurantId(restaurantId);
    }

    @Transactional
    public int deleteByCategoryAndRestaurant(Long categoryId, Long restaurantId) {
        return productRepository.deleteByCategoryIdAndRestaurantId(categoryId, restaurantId);
    }

    // ------------------------------------------------------------------
    // Inventario: descuentos por pedido, ajustes manuales y alertas.
    // ------------------------------------------------------------------

    /**
     * Descuenta existencias por un pedido. Si el producto no rastrea stock
     * no hace nada. Lanza 400 si no hay suficientes existencias.
     */
    @Transactional
    public Product deductStock(Long productId, Long restaurantId, int quantity, Long orderId) {
        Product product = getByIdAndRestaurantIdOrThrow(productId, restaurantId);
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
        Product product = getByIdAndRestaurantIdOrThrow(productId, restaurantId);
        if (!product.isTrackStock()) {
            return;
        }
        product.setStockQuantity(product.getStockQuantity() + quantity);
        productRepository.save(product);
        inventoryService.record(restaurantId, productId, quantity, MovementReason.CANCEL_RESTORE, orderId);
    }

    /** Ajuste manual a una existencia absoluta (reposición o conteo). */
    @Transactional
    public ProductResponse setStockMine(Long productId, int newQuantity, MovementReason reason) {
        Product product = findScoped(productId);
        int delta = newQuantity - product.getStockQuantity();
        product.setStockQuantity(newQuantity);
        Product saved = productRepository.save(product);
        inventoryService.record(product.getRestaurantId(), productId, delta, reason, null);
        return toResponse(saved);
    }

    /** Alerta: productos que rastrean stock y están en o bajo el umbral. */
    @Transactional(readOnly = true)
    public java.util.List<ProductResponse> findLowStockMine() {        Long restaurantId = SecurityUtils.currentRestaurantId();
        return productRepository.findByRestaurantIdOrderByPositionAsc(
                        restaurantId, org.springframework.data.domain.Pageable.unpaged())
                .stream()
                .filter(p -> p.isTrackStock() && p.getStockQuantity() <= p.getLowStockThreshold())
                .map(this::toResponse)
                .toList();
    }

    // ------------------------------------------------------------------
    // Recetas: el plato descuenta ingredientes, no stock propio.
    // ------------------------------------------------------------------

    /**
     * Reemplaza la receta del plato. Cada ingrediente debe ser del tenant.
     * Lista vacía = quitar receta (vuelve a stock propio).
     */
    @Transactional
    public java.util.List<RecipeItem> setRecipeMine(
            Long productId, java.util.List<com.menusaas.inventory.dto.RecipeLineRequest> lines) {
        Product product = findScoped(productId);
        inventoryService.deleteRecipeByProduct(productId);
        if (lines == null || lines.isEmpty()) {
            return java.util.List.of();
        }
        java.util.List<RecipeItem> saved = new java.util.ArrayList<>();
        for (com.menusaas.inventory.dto.RecipeLineRequest line : lines) {
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
        Product product = getByIdAndRestaurantIdOrThrow(productId, restaurantId);
        if (!product.isAvailable()) {
            return false;
        }
        java.util.List<RecipeItem> recipe = inventoryService.findRecipeByProduct(productId);
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
        java.util.List<RecipeItem> recipe = inventoryService.findRecipeByProduct(productId);
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
        Product product = getByIdAndRestaurantIdOrThrow(productId, restaurantId);
        if (product.isTrackStock()) {
            deductStock(product.getId(), restaurantId, quantity, orderId);
        }
    }

    /** Devolución al cancelar: espejo de deductForOrder. */
    @Transactional
    public void restoreForOrder(Long productId, Long restaurantId, int quantity, Long orderId) {
        java.util.List<RecipeItem> recipe = inventoryService.findRecipeByProduct(productId);
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
        Product product = getByIdAndRestaurantIdOrThrow(productId, restaurantId);
        if (product.isTrackStock()) {
            restoreStock(product.getId(), restaurantId, quantity, orderId);
        }
    }

    /** Receta del plato con nombres (para el editor). */
    @Transactional(readOnly = true)
    public java.util.List<com.menusaas.inventory.dto.RecipeItemResponse> getRecipeMine(Long productId) {
        Product product = findScoped(productId);
        java.util.List<RecipeItem> lines = inventoryService.findRecipeByProduct(productId);
        java.util.List<com.menusaas.inventory.dto.RecipeItemResponse> out = new java.util.ArrayList<>();
        for (RecipeItem line : lines) {
            Ingredient ingredient = inventoryService.findIngredientInRestaurant(
                    line.getIngredientId(), product.getRestaurantId());
            out.add(com.menusaas.inventory.dto.RecipeItemResponse.from(line,
                    ingredient != null ? ingredient.getName() : "Ingrediente #" + line.getIngredientId(),
                    ingredient != null ? ingredient.getUnit() : "und"));
        }
        return out;
    }

    private Product findScoped(Long id) {
        return productRepository.findByIdAndRestaurantId(id, SecurityUtils.currentRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
    }

    /**
     * Si trae categoría, debe pertenecer al mismo tenant (evita mover
     * productos entre restaurantes mediante categoryId). Null = sin categoría.
     * Se resuelve vía CategoryService para no tocar su repositorio.
     */
    private void validateCategoryBelongsToTenant(Long categoryId, Long restaurantId) {
        if (categoryId == null) {
            return;
        }
        categoryService.requireInRestaurant(categoryId, restaurantId);
    }

    private ProductResponse toResponse(Product p) {
        return ProductResponse.from(p, signedUrlService.toSignedUrlOrNull(p.getImageUrl()));
    }
}