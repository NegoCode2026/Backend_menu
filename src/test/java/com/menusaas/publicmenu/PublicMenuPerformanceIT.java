package com.menusaas.publicmenu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menusaas.BaseIntegrationTest;
import com.menusaas.TestHttp;
import com.menusaas.publicmenu.dto.PublicMenuResponse;
import com.menusaas.publicmenu.service.PublicMenuService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class PublicMenuPerformanceIT extends BaseIntegrationTest {
    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate sql;
    @Autowired PublicMenuService menus;
    @Autowired EntityManagerFactory emf;

    @Test
    void queryCountStaysConstantWhenTheMenuGrows() throws Exception {
        String slug = "menu-performance";
        TestHttp.register(rest, json, "Carta", "carta-perf@test.com", slug);
        long tenant = sql.queryForObject("select id from restaurants where slug = ?", Long.class, slug);
        long first = sql.queryForObject("insert into categories (restaurant_id, name) values (?, 'Primera') returning id", Long.class, tenant);
        sql.update("insert into products (restaurant_id, category_id, name, price, available) values (?, ?, 'Visible', 1, true), (?, ?, 'Agotado', 1, false), (?, null, 'Suelto', 1, true)", tenant, first, tenant, first, tenant);
        long hidden = sql.queryForObject("insert into categories (restaurant_id, name, active) values (?, 'Oculta', false) returning id", Long.class, tenant);
        sql.update("insert into products (restaurant_id, category_id, name, price) values (?, ?, 'Oculto', 1)", tenant, hidden);

        assertQueryCount(slug, 1);
        sql.update("insert into categories (restaurant_id, name) select ?, 'Sección ' || i from generate_series(1,19) as i", tenant);
        assertQueryCount(slug, 20);
    }

    private void assertQueryCount(String slug, int activeCategories) {
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        boolean previous = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            long start = stats.getPrepareStatementCount();
            var result = menus.getBySlug(slug);
            assertThat(stats.getPrepareStatementCount() - start).as("SQL para %s categorías", activeCategories).isEqualTo(4);
            assertThat(result.categories()).hasSize(activeCategories + 1);
            assertThat(result.categories().getLast().name()).isEqualTo("Sin categoría");
            assertThat(result.categories()).flatExtracting(PublicMenuResponse.CategoryInfo::products)
                    .extracting(PublicMenuResponse.ProductInfo::name).containsExactly("Visible", "Suelto");
        } finally {
            stats.setStatisticsEnabled(previous);
        }
    }
}
