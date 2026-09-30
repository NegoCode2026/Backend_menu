package com.menusaas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    /**
     * flyway.repair() realinea checksums y descarta entradas fallidas. Es
     * seguro solo en una reparación puntual y NO debe correr en cada arranque:
     * un cambio de script ya aplicado pasaría inadvertido y
     * {@code repair()} sobre una migración corrupta oculta el síntoma.
     *
     * <p>Por eso va apagado y hay que encenderlo a propósito
     * (FLYWAY_REPAIR_ON_START=true) solo durante el deploy que lo necesita.
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(
            @Value("${app.flyway.repair-on-start:false}") boolean repairOnStart) {
        return flyway -> {
            if (repairOnStart) {
                flyway.repair();
            }
            flyway.migrate();
        };
    }
}
