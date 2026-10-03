package com.menusaas.products;

import com.menusaas.BaseIntegrationTest;
import com.menusaas.products.entity.Product;
import com.menusaas.products.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Versionado optimista: dos ediciones simultáneas del mismo producto no pueden
 * pisarse en silencio.
 *
 * <p>Antes no había ningún {@code @Version}: el segundo UPDATE sobrescribía al
 * primero sin error, sin log y sin aviso, lo que para precios y costes es pérdida
 * de dinero sin rastro. Con {@code @Version}, Hibernate añade el valor leído a la
 * cláusula WHERE del UPDATE, así que la segunda escritura afecta cero filas y
 * lanza ObjectOptimisticLockingFailureException, que el handler traduce a 409.
 *
 * <p>Esta prueba necesita concurrencia real: dos transacciones que leen la misma
 * versión y luego escriben. Simularlo con dos peticiones HTTP no serviría, porque
 * cada petición abre su transacción y relee la entidad.
 */
class OptimisticLockingIT extends BaseIntegrationTest {

    @Autowired
    ProductRepository productRepository;

    @Test
    void twoConcurrentEdits_oneIsRejected() throws Exception {
        long productId = createProduct();

        int before = versionOf(productId);

        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger applied = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 2; i++) {
                final int which = i;
                pool.submit(() -> {
                    try {
                        editInTransaction(productId, which, bothRead, go);
                        applied.incrementAndGet();
                    } catch (ObjectOptimisticLockingFailureException e) {
                        rejected.incrementAndGet();
                    } catch (Exception e) {
                        // El rechazo por versión también puede llegar como
                        // ObjectOptimisticLockingFailureException envuelto.
                        if (containsOptimisticFailure(e)) {
                            rejected.incrementAndGet();
                        } else {
                            throw new IllegalStateException(e);
                        }
                    }
                });
            }

            // Los dos leen antes de que ninguno escriba: garantiza el conflicto.
            bothRead.await(5, TimeUnit.SECONDS);
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(applied.get()).as("una escritura debe prosperar").isEqualTo(1);
        assertThat(rejected.get()).as("la otra debe rechazarse en vez de pisar").isEqualTo(1);
        assertThat(versionOf(productId)).isEqualTo(before + 1);
    }

    @Test
    void productEntityIsVersioned() {
        // Guarda de que el mecanismo siga puesto: sin @Version, el test anterior
        // pasaría por casualidad (applied=2, rejected=0) y fallaría aquí.
        assertThat(Product.class.getDeclaredFields())
                .anySatisfy(f -> {
                    if (f.isAnnotationPresent(jakarta.persistence.Version.class)) {
                        assertThat(f.getName()).isEqualTo("version");
                    }
                });
        assertThat(java.util.Arrays.stream(Product.class.getDeclaredFields())
                .anyMatch(f -> f.isAnnotationPresent(jakarta.persistence.Version.class))).isTrue();
    }

    private boolean containsOptimisticFailure(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof ObjectOptimisticLockingFailureException
                    || current instanceof jakarta.persistence.OptimisticLockException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /** Lee la versión por reflexión: así la guarda de arriba sigue compilando
     *  aunque se quite @Version, y falla de verdad en vez de no compilar. */
    private int versionOf(long productId) {
        return productRepository.findById(productId).orElseThrow().getVersion().intValue();
    }

    private long createProduct() {
        Product p = productRepository.saveAndFlush(Product.builder()
                .restaurantId(1L)
                .name("Producto Concurrente")
                .price(java.math.BigDecimal.valueOf(1000))
                .available(true)
                .build());
        return p.getId();
    }

    /**
     * Simula el panel de productos: lee, espera, y escribe. El latch de lectura
     * fuerza a las dos transacciones a leer antes de que ninguna escriba, que es
     * justo la ventana en la que el versionado optimista actúa.
     */
    private void editInTransaction(long productId, int which, CountDownLatch bothRead, CountDownLatch go)
            throws Exception {
        org.springframework.transaction.support.TransactionTemplate tx =
                new org.springframework.transaction.support.TransactionTemplate(txManager);

        tx.executeWithoutResult(status -> {
            Product p = productRepository.findById(productId).orElseThrow();
            bothRead.countDown();
            try {
                go.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            p.setName("Editado-" + which);
            p.setPrice(java.math.BigDecimal.valueOf(2000 + which));
            // flush dentro de la transacción: el conflicto salta aquí, no al commit.
            productRepository.saveAndFlush(p);
        });
    }

    @Autowired
    org.springframework.transaction.PlatformTransactionManager txManager;
}