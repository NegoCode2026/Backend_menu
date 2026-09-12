package com.menusaas.shared.security;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Rate limiter en memoria con ventana fija: cuenta peticiones por clave
 * (IP + endpoint) dentro de una ventana de un minuto.
 * Los contadores se limpian de forma perezosa: una entrada que no recibe
 * peticiones en varias ventanas se sobrescribe/reutiliza en cuanto un nuevo
 * intento abre una nueva ventana.
 */
@Component
public class RateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private record Entry(long windowStartMillis, int count) {
    }

    private final ConcurrentMap<String, Entry> buckets = new ConcurrentHashMap<>();

    /**
     * @return true si la petición supera el límite permitido en la ventana.
     */
    public boolean isLimited(String key, int maxPerMinute) {
        if (maxPerMinute <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        Entry current = buckets.compute(key, (k, entry) -> {
            if (entry == null || entry.windowStartMillis() + WINDOW_MILLIS < now) {
                return new Entry(now, 1);
            }
            return new Entry(entry.windowStartMillis(), entry.count() + 1);
        });
        return current.count() > maxPerMinute;
    }
}