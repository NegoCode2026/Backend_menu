package com.menusaas.products;

import com.menusaas.categories.repository.CategoryRepository;
import com.menusaas.inventory.entity.Ingredient;
import com.menusaas.inventory.entity.MovementReason;
import com.menusaas.inventory.entity.RecipeItem;
import com.menusaas.inventory.repository.IngredientRepository;
import com.menusaas.inventory.repository.RecipeItemRepository;
import com.menusaas.inventory.service.InventoryService;
import com.menusaas.products.entity.Product;
import com.menusaas.products.repository.ProductRepository;
import com.menusaas.products.service.ProductService;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.security.SignedUrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * El plato con receta descuenta ingredientes, no stock propio.
 */
@ExtendWith(MockitoExtension.class)
class RecipeStockTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private SignedUrlService signedUrlService;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private IngredientRepository ingredientRepository;
    @Mock
    private RecipeItemRepository recipeItemRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, categoryRepository,
                signedUrlService, inventoryService, ingredientRepository, recipeItemRepository);
    }

    private Product dish(Long id) {
        return Product.builder().id(id).restaurantId(1L).name("Hamburguesa")
                .price(new BigDecimal("18000")).costPrice(new BigDecimal("8000"))
                .stockQuantity(99).trackStock(false).available(true).build();
    }

    private Ingredient cheese(BigDecimal stock) {
        return Ingredient.builder().id(10L).restaurantId(1L).name("Queso").unit("g")
                .stockQuantity(stock).lowStockThreshold(new BigDecimal("500"))
                .trackStock(true).build();
    }

    private RecipeItem line() {
        return RecipeItem.builder().id(1L).productId(1L).ingredientId(10L)
                .quantity(new BigDecimal("100")).build();
    }

    @Test
    void deductForOrder_withRecipe_discountsIngredients() {
        when(recipeItemRepository.findByProductId(1L)).thenReturn(List.of(line()));
        when(ingredientRepository.findByIdAndRestaurantId(10L, 1L))
                .thenReturn(Optional.of(cheese(new BigDecimal("1000"))));
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.deductForOrder(1L, 1L, 2, 99L);

        verify(ingredientRepository).save(argThat(i ->
                i.getStockQuantity().compareTo(new BigDecimal("800")) == 0));
        verify(inventoryService).recordIngredient(eq(1L), eq(10L),
                argThat(q -> q.compareTo(new BigDecimal("-200")) == 0),
                eq(MovementReason.ORDER), eq(99L));
        verify(productRepository, never()).save(any());
    }

    @Test
    void deductForOrder_withoutIngredients_throws400() {
        when(recipeItemRepository.findByProductId(1L)).thenReturn(List.of(line()));
        when(ingredientRepository.findByIdAndRestaurantId(10L, 1L))
                .thenReturn(Optional.of(cheese(new BigDecimal("50"))));

        assertThatThrownBy(() -> productService.deductForOrder(1L, 1L, 2, 99L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Queso");
    }

    @Test
    void canFulfill_respectsRecipe() {
        when(productRepository.findByIdAndRestaurantId(1L, 1L))
                .thenReturn(Optional.of(dish(1L)));
        when(recipeItemRepository.findByProductId(1L)).thenReturn(List.of(line()));
        when(ingredientRepository.findByIdAndRestaurantId(10L, 1L))
                .thenReturn(Optional.of(cheese(new BigDecimal("1000"))));

        assertThat(productService.canFulfill(1L, 1L, 5)).isTrue();
        assertThat(productService.canFulfill(1L, 1L, 50)).isFalse();
    }
}
