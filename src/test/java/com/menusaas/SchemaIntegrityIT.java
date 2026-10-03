package com.menusaas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Blindaje del esquema contra la deriva migración ↔ base de datos real.
 *
 * <p>El fallo que motivó este test: {@code restaurant_tables} se creó con la
 * columna {@code table_number} mientras la entidad JPA mapeaba {@code number},
 * y {@code GET/POST /api/tables} devolvía 500 ({@code column rt1_0.number does
 * not exist}). Nadie lo notó porque el {@code flyway.repair()} que corría en
 * cada arranque aceptaba la deriva de checksum en lugar de señalarla, y el
 * Dockerfile fuerza el perfil {@code prod}, donde {@code ddl-auto} es
 * {@code none}: nada validaba el esquema al arrancar. El error solo aparecía
 * al usar el endpoint.
 *
 * <p>Este test no vale por sí solo: los tests de integración ya construyen la
 * base desde las migraciones y, con {@code ddl-auto=validate} en el perfil por
 * defecto, Hibernate revisa entidad contra esquema. Lo que faltaba era una
 * comprobación explícita de que las tablas que el código da por hecho existen
 * de verdad, porque {@code validate} aborta en el primer fallo y las entidades
 * no cubren tablas sin entidad (como {@code audit_log}, que casi no se usa).
 */
class SchemaIntegrityIT extends BaseIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    /** Tablas sin las que el código falla en runtime, no al arrancar. */
    private static final List<String> REQUIRED_TABLES = List.of(
            "restaurants", "users", "roles", "refresh_tokens", "categories", "products",
            "orders", "order_items", "order_status_history", "subscriptions", "plans",
            "stored_files", "audit_log", "restaurant_tables", "stock_movements",
            "ingredients", "recipe_items", "cash_closings", "role_permissions",
            "user_permissions"
    );

    /** Columnas cuyo nombre no se deduce solo y ya han causado un 500. */
    private static final List<String[]> REQUIRED_COLUMNS = List.of(
            new String[]{"restaurant_tables", "number"},
            new String[]{"audit_log", "actor_email"},
            new String[]{"audit_log", "action"},
            new String[]{"subscriptions", "provider_reference"}
    );

    @Test
    void everyTableTheCodeReliesOn_exists() {
        for (String table : REQUIRED_TABLES) {
            Object found = jdbc.queryForObject(
                    "select to_regclass(?)", Object.class, "public." + table);
            assertThat(found)
                    .as("tabla ausente: %s. Si una migración figura aplicada pero la tabla "
                            + "no existe, flyway.repair() la está enmascarando: repair no "
                            + "re-ejecuta migraciones ya aplicadas.", table)
                    .isNotNull();
        }
    }

    @Test
    void everyColumnTheCodeReliesOn_exists() {
        for (String[] tableAndColumn : REQUIRED_COLUMNS) {
            String table = tableAndColumn[0];
            String column = tableAndColumn[1];
            Integer count = jdbc.queryForObject(
                    "select count(*) from information_schema.columns "
                            + "where table_schema='public' and table_name=? and column_name=?",
                    Integer.class, table, column);
            assertThat(count)
                    .as("columna ausente: %s.%s", table, column)
                    .isEqualTo(1);
        }
    }

    @Test
    void auditLogIsWritableAndReadable() {
        // audit_log casi solo se escribe y no se lee; su ausencia en una base real
        // pasó desapercibida porque AuditService.log() es best-effort y captura el
        // error, mientras list() no. Se comprueba el ciclo completo.
        Long before = jdbc.queryForObject("select count(*) from audit_log", Long.class);
        jdbc.update("insert into audit_log (actor_email, action, entity_type) values (?, ?, ?)",
                "schema-check@test.com", "SCHEMA_CHECK", "schema");
        Long after = jdbc.queryForObject("select count(*) from audit_log", Long.class);
        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    void restaurantTableNumberColumn_isUsableWithTheEntityMapping() {
        // El 500 original venía de aquí: la entidad genera "number" en el SQL.
        // Esta consulta es exactamente la que Hibernate ejecutaba.
        Integer count = jdbc.queryForObject(
                "select count(*) from restaurant_tables where number = ?", Integer.class, "1");
        assertThat(count).isNotNull();
    }
}