package com.menusaas.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menusaas.BaseIntegrationTest;
import com.menusaas.TestHttp;
import com.menusaas.inventory.entity.Ingredient;
import com.menusaas.products.entity.Product;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryConcurrencyIT extends BaseIntegrationTest {
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate sql;
    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper json;

    @Test
    void overlappingCountsDetectStaleProductsAndIngredients() throws Exception {
        TestHttp.register(rest, json, "Conteo", "conteo-race@test.com", "conteo-race");
        long tenant = sql.queryForObject("select id from restaurants where slug = 'conteo-race'", Long.class);
        long product = sql.queryForObject("insert into products (restaurant_id, name, price, stock_quantity) values (?, 'Plato', 10, 2) returning id", Long.class, tenant);
        long ingredient = sql.queryForObject("insert into ingredients (restaurant_id, name, unit, stock_quantity) values (?, 'Arroz', 'g', 2) returning id", Long.class, tenant);
        assertOneCommit(() -> {
            Product row = em.find(Product.class, product);
            return () -> row.setStockQuantity(row.getStockQuantity() - 1);
        });
        assertOneCommit(() -> {
            Ingredient row = em.find(Ingredient.class, ingredient);
            return () -> row.setStockQuantity(row.getStockQuantity().subtract(BigDecimal.ONE));
        });
        assertThat(sql.queryForObject("select stock_quantity from products where id = ?", Integer.class, product)).isOne();
        assertThat(sql.queryForObject("select stock_quantity from ingredients where id = ?", Integer.class, ingredient)).isOne();
    }

    private void assertOneCommit(Supplier<Runnable> readThenUpdate) throws Exception {
        var bothRead = new CyclicBarrier(2);
        Callable<Boolean> count = () -> {
            try {
                tx.executeWithoutResult(transaction -> {
                    Runnable update = readThenUpdate.get();
                    try {
                        bothRead.await(10, TimeUnit.SECONDS);
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                    update.run();
                });
                return true;
            } catch (OptimisticLockingFailureException conflict) {
                return false;
            }
        };
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(count);
            var b = pool.submit(count);
            assertThat(new Boolean[]{a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
    }
}
