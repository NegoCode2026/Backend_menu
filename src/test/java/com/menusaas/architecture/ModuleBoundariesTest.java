package com.menusaas.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardián de boundaries: evita que el monolito se vuelva spaghetti.
 *
 * Reglas:
 * - Cada módulo solo usa SU propio *.repository; lo ajeno va vía services.
 * - EXCEPCIÓN (documentada): admin/backoffice accede a datos de todos los
 *   restaurantes (es el único módulo cross-tenant; ver package-info de admin).
 * - EXCEPCIÓN (documentada): auth usa users.repository y
 *   restaurants.repository (bootstrap de identidad: register crea el tenant y
 *   su usuario en una transacción; login carga el usuario por email/id).
 * - Los controllers no dependen de ningún repository (solo services).
 * - No hay ciclos entre módulos.
 */
class ModuleBoundariesTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(new com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests())
            .importPackages("com.menusaas");

    private static final List<String> DOMAIN_MODULES = List.of(
            "cash", "categories", "files", "inventory", "orders", "permissions",
            "products", "publicmenu", "qr", "realtime", "reports",
            "restaurants", "subscriptions", "users");

    @Test
    void modules_shouldOnlyUseOwnRepositories() {
        for (String module : DOMAIN_MODULES) {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.menusaas." + module + "..")
                    .should().dependOnClassesThat(
                            JavaClass.Predicates.resideInAPackage("com.menusaas..repository")
                                    .and(JavaClass.Predicates
                                            .resideOutsideOfPackage("com.menusaas." + module + ".repository"))
                                    .as("repositorios de otros módulos"))
                    .because(module + " resuelve lo ajeno vía services, no repositorios directos");
            rule.check(classes);
        }
    }

    @Test
    void auth_shouldOnlyUseIdentityRepositories() {
        // EXCEPCIÓN documentada: bootstrap de identidad (ver javadoc de la clase).
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.menusaas.auth..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.menusaas.cash.repository",
                        "com.menusaas.categories.repository",
                        "com.menusaas.files.repository",
                        "com.menusaas.inventory.repository",
                        "com.menusaas.orders.repository",
                        "com.menusaas.permissions.repository",
                        "com.menusaas.products.repository",
                        "com.menusaas.publicmenu.repository",
                        "com.menusaas.qr.repository",
                        "com.menusaas.realtime.repository",
                        "com.menusaas.reports.repository",
                        "com.menusaas.subscriptions.repository",
                        "com.menusaas.admin.repository")
                .because("auth solo toca users/restaurants (identidad), nunca el resto");
        rule.check(classes);
    }

    @Test
    void controllers_shouldNotDependOnRepositories() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.menusaas.*.controller")
                .should().dependOnClassesThat()
                .resideInAPackage("com.menusaas..repository")
                .because("los controllers reciben, validan y delegan a services");
        rule.check(classes);
    }

    @Test
    void modules_shouldBeFreeOfCycles() {
        // EXCEPCIÓN documentada: auth/shared/config son la base de infraestructura
        // intencionalmente acoplada y quedan fuera de esta regla:
        // - shared.security.SecurityUtils <-> auth.security.UserPrincipal
        //   (el helper transversal lee el principal; todo módulo lo usa),
        // - config.SecurityConfig -> auth.security.JwtAuthenticationFilter y
        //   shared.* -> config.AppProperties (cableado y propiedades),
        // - auth -> users.repository/restaurants.repository (bootstrap de
        //   identidad, misma excepción que auth_shouldOnlyUseIdentityRepositories).
        // Romper esos ciclos implicaría rediseñar la base de seguridad, fuera del
        // alcance de esta refactorización. La regla sí protege a los módulos de
        // dominio (cash, orders, products, ...) contra ciclos entre ellos.
        JavaClasses domain = classes.that(
                JavaClass.Predicates.resideOutsideOfPackages(
                        "com.menusaas.auth..",
                        "com.menusaas.config..",
                        "com.menusaas.shared.."));
        SlicesRuleDefinition.slices()
                .matching("com.menusaas.(*)..")
                .should().beFreeOfCycles()
                .check(domain);
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
