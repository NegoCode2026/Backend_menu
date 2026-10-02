package com.menusaas.products.repository;

import com.menusaas.products.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByRestaurantIdOrderByPositionAsc(Long restaurantId, Pageable pageable);

    Page<Product> findByCategoryIdAndRestaurantIdOrderByPositionAsc(Long categoryId, Long restaurantId, Pageable pageable);

    Optional<Product> findByIdAndRestaurantId(Long id, Long restaurantId);

    java.util.List<Product> findByRestaurantIdAndCategoryIdIsNullOrderByPositionAsc(Long restaurantId);

    @Query("""
            select p from Product p
            where p.categoryId = :categoryId
              and p.restaurantId = :restaurantId
              and (:onlyAvailable = false or p.available = true)
            order by p.position asc, p.name asc
            """)
    List<Product> findByCategoryScoped(@Param("categoryId") Long categoryId,
                                       @Param("restaurantId") Long restaurantId,
                                       @Param("onlyAvailable") boolean onlyAvailable);

    /**
     * Conteo de productos agrupado por restaurante (panel admin: evita N+1).
     */
    /**
     * Todos los productos del restaurante en UNA query, para agruparlos por
     * categoría en memoria. Evita el N+1 de consultar categoría por categoría que
     * suffer el menú público (el endpoint de más tráfico, sin autenticar).
     */
    @Query("""
            select p from Product p
            where p.restaurantId = :restaurantId
              and p.available = true
            order by p.position asc, p.name asc
            """)
    List<Product> findAvailableByRestaurantId(@Param("restaurantId") Long restaurantId);

    @Query("select p.restaurantId, count(p) from Product p where p.restaurantId in :ids group by p.restaurantId")
    List<Object[]> countGroupedByRestaurantIds(@Param("ids") java.util.Collection<Long> ids);
}