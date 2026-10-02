package com.menusaas.shared.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El limitador en memoria acotó su mapa porque la clave incluye la IP: un
 * atacante que varyase IP generaba una entrada por intento y el mapa crecía sin
 * límite (fuga de memoria).
 */
class RateLimiterTest {

    @Test
    void allowsUpToTheLimit_andBlocksBeyond() {
        RateLimiter limiter = new RateLimiter(1000);

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.isLimited("1.2.3.4|/api/auth/login", 3)).isFalse();
        }
        assertThat(limiter.isLimited("1.2.3.4|/api/auth/login", 3)).isTrue();
    }

    @Test
    void keysAreIndependent() {
        RateLimiter limiter = new RateLimiter(1000);

        assertThat(limiter.isLimited("a|path", 1)).isFalse();
        assertThat(limiter.isLimited("b|path", 1)).isFalse();
        assertThat(limiter.isLimited("a|path", 1)).isTrue();
        assertThat(limiter.isLimited("b|path", 1)).isTrue();
    }

    @Test
    void keyFloodIsCappedInsteadOfGrowingForever() {
        // Tope diminuto para simular el caso real sin llenar 20000 entradas.
        RateLimiter limiter = new RateLimiter(10);

        for (int i = 0; i < 5000; i++) {
            limiter.isLimited("10.0.0." + (i % 256) + "|" + i, 60);
        }

        // El mapa no crece sin límite: se queda cerca del tope.
        assertThat(limiter.trackedKeys()).isLessThanOrEqualTo(10);
        // Y la poda llegó a ejecutarse al menos una vez.
        assertThat(limiter.purgeCount()).isPositive();
    }

    @Test
    void whenAtCapAndNothingExpired_newTrafficIsRejected() {
        // Con el tope lleno y sin ventanas caducadas, se rechaza antes de crecer.
        // Proteger la memoria tiene prioridad sobre contabilizar la petición.
        RateLimiter limiter = new RateLimiter(5);

        for (int i = 0; i < 5; i++) {
            limiter.isLimited("clave-" + i, 60);
        }
        assertThat(limiter.isLimited("clave-nueva", 60)).isTrue();
        assertThat(limiter.trackedKeys()).isLessThanOrEqualTo(5);
    }

    @Test
    void limitZeroOrNegative_disablesLimiting() {
        RateLimiter limiter = new RateLimiter(1000);

        for (int i = 0; i < 100; i++) {
            assertThat(limiter.isLimited("k", 0)).isFalse();
            assertThat(limiter.isLimited("k", -5)).isFalse();
        }
    }
}