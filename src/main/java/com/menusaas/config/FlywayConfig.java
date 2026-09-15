package com.menusaas.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        // Reparación puntual: realinea el checksum de V2 en la BD de producción
        // (editado después de haberse aplicado). Quitar cuando el deploy pase.
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}