package com.menusaas.products;

import com.menusaas.categories.service.CategoryService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryStockTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private SignedUrlService signedUrlService;

    @Mock
    private InventoryService inventoryService;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, categoryService, signedUrlService, inventoryService);
    }

    private Product tracked(Long id, int stock) {
        return Product.builder()
                .id(id).restaurantId(1L).categoryId(1L).name("Papa")
                .price(new BigDecimal("10000")).costPrice(new BigDecimal("4000"))
                .stockQuantity(stock).lowStockThreshold(5).trackStock(true)
                .available(true).build();
    }

    @Test
    void deductStock_reducesAndRecordsMovement() {
        when(productRepository.findByIdAndRestaurantId(1L, 1L)).thenReturn(Optional.of(tracked(1L, 10)));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product updated = productService.deductStock(1L, 1L, 3, 99L);

        assertThat(updated.getStockQuantity()).isEqualTo(7);
        verify(inventoryService).record(eq(1L), eq(1L), eq(-3),
                eq(com.menusaas.inventory.entity.MovementReason.ORDER), eq(99L));
    }

    @Test
    void deductStock_throwsWhenInsufficient() {
        when(productRepository.findByIdAndRestaurantId(1L, 1L)).thenReturn(Optional.of(tracked(1L, 2)));

        assertThatThrownBy(() -> productService.deductStock(1L, 1L, 5, 99L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Stock insuficiente");
        verify(productRepository, never()).save(any());
    }

    @Test
    void deductStock_skipsWhenNotTracked() {
        Product plain = tracked(1L, 0);
        plain.setTrackStock(false);
        when(productRepository.findByIdAndRestaurantId(1L, 1L)).thenReturn(Optional.of(plain));

        productService.deductStock(1L, 1L, 5, 99L);

        verify(productRepository, never()).save(any());
        verify(inventoryService, never()).record(any(), any(), anyInt(), any(), any());
    }

    @Test
    void restoreStock_addsBack() {
        when(productRepository.findByIdAndRestaurantId(1L, 1L)).thenReturn(Optional.of(tracked(1L, 7)));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.restoreStock(1L, 1L, 3, 99L);

        verify(productRepository).save(argThat(p -> p.getStockQuantity() == 10));
        verify(inventoryService).record(eq(1L), eq(1L), eq(3),
                eq(com.menusaas.inventory.entity.MovementReason.CANCEL_RESTORE), eq(99L));
    }
}

