package com.menusaas.products;

import com.menusaas.categories.service.CategoryService;
import com.menusaas.inventory.service.InventoryService;
import com.menusaas.products.service.ProductRecipeService;
import com.menusaas.products.dto.ProductRequest;
import com.menusaas.products.entity.Product;
import com.menusaas.products.repository.ProductRepository;
import com.menusaas.products.service.ProductService;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
import com.menusaas.shared.security.SignedUrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private SignedUrlService signedUrlService;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private ProductRecipeService recipeService;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, categoryService, signedUrlService, inventoryService, recipeService);
    }

    @Test
    void create_rejectsCategoryFromAnotherTenant() {
        // El "atacante" del restaurante 2 intenta crear un producto
        // con categoryId del restaurante 1.
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(2L);
            doThrow(new ResourceNotFoundException("Categoría no encontrada en este restaurante"))
                    .when(categoryService).requireInRestaurant(999L, 2L);

            ProductRequest request = new ProductRequest(999L, "X", null, new BigDecimal("100"), null, true, 0,
                    null, null, null, null);

            assertThatThrownBy(() -> productService.createMine(request))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(productRepository, never()).save(any());
        }
    }

    @Test
    void create_persistsProductScopedToCurrentTenant() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(2L);
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
                Product p = inv.getArgument(0);
                p.setId(1L);
                return p;
            });

            ProductRequest request = new ProductRequest(7L, "Hamburguesa", "Deliciosa",
                    new BigDecimal("18000.00"), null, true, 1, null, null, null, null);

            var response = productService.createMine(request);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.restaurantId()).isEqualTo(2L);
            assertThat(response.price()).isEqualByComparingTo("18000.00");
            verify(productRepository).save(argThat(p ->
                    p.getRestaurantId() == 2L && p.getCategoryId() == 7L && "Hamburguesa".equals(p.getName())));
        }
    }

    @Test
    void update_404WhenProductBelongsToAnotherTenant() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            when(productRepository.findByIdAndRestaurantId(5L, 1L)).thenReturn(Optional.empty());

            ProductRequest request = new ProductRequest(1L, "Y", null, new BigDecimal("1"), null, true, 0,
                    null, null, null, null);

            assertThatThrownBy(() -> productService.updateMine(5L, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    void delete_rejectsProductOfAnotherTenant() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            when(productRepository.findByIdAndRestaurantId(5L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.deleteMine(5L))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(productRepository, never()).delete(any());
        }
    }
}
