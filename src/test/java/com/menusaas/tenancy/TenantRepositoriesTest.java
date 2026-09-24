package com.menusaas.tenancy;

import com.menusaas.cash.repository.CashClosingRepository;
import com.menusaas.categories.repository.CategoryRepository;
import com.menusaas.inventory.repository.IngredientRepository;
import com.menusaas.inventory.repository.StockMovementRepository;
import com.menusaas.orders.repository.OrderRepository;
import com.menusaas.permissions.repository.RolePermissionRepository;
import com.menusaas.products.repository.ProductRepository;
import com.menusaas.subscriptions.repository.SubscriptionRepository;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los repositorios de entidades {@code TenantOwned} solo exponen consultas con
 * tenant. Dos reglas:
 *
 * 1) Todo método DECLARADO debe mencionar al tenant en su nombre
 *    (…ByRestaurantId…, …ByRestaurant…), salvo excepciones documentadas abajo.
 *    Los heredados de JpaRepository (findById, findAll, …) no se pueden quitar
 *    de la interfaz; los cubre la regla 2.
 * 2) Ningún código fuera de admin/backoffice llama a findById/deleteById/
 *    getReferenceById sobre esos repositorios (los métodos heredados
 *    riesgosos). admin es cross-tenant por diseño (SUPER_ADMIN).
 */
class TenantRepositoriesTest {

    private static final List<Class<?>> TENANT_REPOSITORIES = List.of(
            CashClosingRepository.class,
            CategoryRepository.class,
            IngredientRepository.class,
            StockMovementRepository.class,
            OrderRepository.class,
            RolePermissionRepository.class,
            ProductRepository.class,
            SubscriptionRepository.class);

    /**
     * Métodos sin "Restaurant" en el nombre, revisados uno por uno:
     * - OrderRepository.findByTrackingCode: tracking por código UUID no
     *   adivinable (endpoint público de seguimiento).
     * - OrderRepository.sumTotalSince: filtrado por restaurantId dentro del
     *   @Query (verificado en la interfaz).
     * - ProductRepository.findByCategoryScoped: filtrado por restaurantId
     *   dentro del @Query (verificado en la interfaz).
     * - SubscriptionRepository.findByProviderReference: búsqueda por referencia
     *   externa del webhook (no enumerable).
     * - SubscriptionRepository.findByStatusAndEndsAtBefore/countByStatus:
     *   barridos globales del job de expiración y del panel admin.
     */
    private static final Map<Class<?>, Set<String>> ALLOWED_UNSCOPED = Map.of(
            OrderRepository.class, Set.of("findByTrackingCode", "sumTotalSince"),
            ProductRepository.class, Set.of("findByCategoryScoped"),
            SubscriptionRepository.class, Set.of(
                    "findByProviderReference", "findByStatusAndEndsAtBefore", "countByStatus"));

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(new com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests())
            .importPackages("com.menusaas");

    @Test
    void tenantRepositories_declareOnlyScopedQueries() {
        List<String> violations = new ArrayList<>();
        for (Class<?> repo : TENANT_REPOSITORIES) {
            Set<String> allowed = ALLOWED_UNSCOPED.getOrDefault(repo, Set.of());
            for (Method m : repo.getDeclaredMethods()) {
                if (m.isSynthetic()) {
                    continue;
                }
                if (!m.getName().contains("Restaurant") && !allowed.contains(m.getName())) {
                    violations.add(repo.getSimpleName() + "#" + m.getName());
                }
            }
        }
        assertThat(violations)
                .as("métodos de repositorio sin tenant (usa …ByRestaurantId… o documenta la excepción)")
                .isEmpty();
    }

    @Test
    void tenantRepositories_unscopedInheritedMethods_onlyUsedByAdmin() {
        for (Class<?> repo : TENANT_REPOSITORIES) {
            for (String method : List.of("findById", "deleteById", "getReferenceById")) {
                ArchRule rule = noClasses()
                        .that().resideOutsideOfPackage("com.menusaas.admin..")
                        .should().callMethod(repo, method, Object.class)
                        .because(repo.getSimpleName() + "." + method
                                + " no filtra por tenant; usa las variantes …AndRestaurantId");
                rule.check(classes);
            }
        }
    }
}
