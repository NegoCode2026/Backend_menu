package com.menusaas.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiter en memoria con ventana fija: cuenta peticiones por clave
 * (IP + endpoint) dentro de una ventana de un minuto.
 *
 * <p><b>El mapa está acotado a propósito.</b> La clave incluye la IP del
 * cliente, así que un atacante que varíe IP (o porteá en X-Forwarded-For)
 * generaba una entrada por cada intento y el mapa crecía sin límite: fuga de
 * memoria lenta pero real. Cuando se alcanza el tope se purgan las ventanas ya
 * caducas; si siguen sin sobrar sitio, se rechaza el tráfico nuevo en vez de
 * seguir creciendo.
 *
 * <p>Nota: al ser memoria del proceso, el límite efectivo es este número
 * multiplicado por el número de réplicas. Con una sola instancia es exacto.
 */
@Component
public class RateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private record Entry(long windowStartMillis, int count) {
    }

    private final ConcurrentMap<String, Entry> buckets = new ConcurrentHashMap<>();

    /** Techo de claves vivas; por encima se purga en lugar de crecer. */
    private final int maxKeys;

    /** Contador de purgas, para observar que la poda está actuando. */
    private final AtomicLong purgeCount = new AtomicLong();

    public RateLimiter(@Value("${app.rate-limiting.max-keys:20000}") int maxKeys) {
        this.maxKeys = maxKeys <= 0 ? 20_000 : maxKeys;
    }

    /**
     * @return true si la petición supera el límite permitido en la ventana.
     */
    public boolean isLimited(String key, int maxPerMinute) {
        if (maxPerMinute <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();

        if (buckets.size() >= maxKeys && !purgeExpired(now)) {
            // Sin espacio y nada caducado: no crecer más. Se trata como limitado
            // para proteger la memoria; el cliente recibe 429 y lo reintenta.
            return true;
        }

        Entry current = buckets.compute(key, (k, entry) -> {
            if (entry == null || entry.windowStartMillis() + WINDOW_MILLIS < now) {
                return new Entry(now, 1);
            }
            return new Entry(entry.windowStartMillis(), entry.count() + 1);
        });
        return current.count() > maxPerMinute;
    }

    /**
     * Elimina las ventanas ya vencidas. Se recorre el mapa entero, que es
     * aceptable: solo se dispara al llegar al tope, no en cada petición.
     */
    private boolean purgeExpired(long now) {
        int before = buckets.size();
        Iterator<Map.Entry<String, Entry>> it = buckets.entrySet().iterator();
        while (it.hasNext()) {
            Entry entry = it.next().getValue();
            if (entry.windowStartMillis() + WINDOW_MILLIS < now) {
                it.remove();
            }
        }
        purgeCount.incrementAndGet();
        return buckets.size() < before;
    }

    /** Solo para pruebas y diagnóstico. */
    int trackedKeys() {
        return buckets.size();
    }

    long purgeCount() {
        return purgeCount.get();
    }
}