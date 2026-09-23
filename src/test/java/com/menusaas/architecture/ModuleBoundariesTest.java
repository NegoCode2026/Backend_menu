package com.menusaas.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardián de boundaries: evita que el monolito se vuelva spaghetti.
 *
 * Reglas:
 * - orders/publicmenu no tocan repositorios ajenos (van vía servicios).
 * - Nadie importa el paquete viejo menus ni files.security (salvo el shim).
 * - admin.* es la única excepción legítima para agregados cross-tenant.
 */
class ModuleBoundariesTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(new com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests())
            .importPackages("com.menusaas");

    @Test
    void orders_shouldNotAccessForeignRepositories() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.menusaas.orders..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.menusaas.products.repository")
                .orShould().dependOnClassesThat()
                .resideInAPackage("com.menusaas.restaurants.repository")
                .orShould().dependOnClassesThat()
                .resideInAPackage("com.menusaas.categories.repository")
                .because("orders resuelve catálogo vía ProductService/RestaurantService, no repositorios ajenos");
        rule.check(classes);
    }

    @Test
    void publicmenu_shouldNotAccessForeignRepositories() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.menusaas.publicmenu..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.menusaas.products.repository")
                .orShould().dependOnClassesThat()
                .resideInAPackage("com.menusaas.restaurants.repository")
                .orShould().dependOnClassesThat()
                .resideInAPackage("com.menusaas.categories.repository")
                .because("publicmenu resuelve vía servicios de dominio");
        rule.check(classes);
    }

    @Test
    void categories_shouldNotAccessProductRepositoryDirectly() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.menusaas.categories..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.menusaas.products.repository")
                .because("categories muta productos vía ProductService.deleteByCategoryAndRestaurant");
        rule.check(classes);
    }

    @Test
    void oldMenusPackage_shouldNotExist() {
        assertThat(classes.stream()
                .noneMatch(c -> c.getPackageName().startsWith("com.menusaas.menus")))
                .as("paquete com.menusaas.menus fue renombrado a publicmenu")
                .isTrue();
    }

    @Test
    void signedUrlService_shouldLiveInSharedSecurity() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages("com.menusaas.files.security", "com.menusaas.shared.security")
                .should().dependOnClassesThat()
                .resideInAPackage("com.menusaas.files.security")
                .because("SignedUrlService vive en shared.security; files.security solo mantiene el shim @Deprecated");
        rule.check(classes);
    }
}
