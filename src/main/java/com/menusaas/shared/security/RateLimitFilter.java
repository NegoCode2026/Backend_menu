package com.menusaas.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Limita la tasa de peticiones por IP en puntos de entrada sensibles:
 * autenticación (fuerza bruta) y creación de pedidos públicos (abuso/spam).
 * Detrás de un proxy se confía en X-Forwarded-For; el primer valor es el
 * cliente real que inyectó el proxy.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh"
    );

    private static final String PUBLIC_ORDERS_PREFIX = "/api/public/orders/";

    private final RateLimiter rateLimiter;
    private final int maxPerMinute;
    private final int publicOrdersMaxPerMinute;

    public RateLimitFilter(RateLimiter rateLimiter,
                           @Value("${app.rate-limiting.auth-max-per-minute:20}") int maxPerMinute,
                           @Value("${app.rate-limiting.public-orders-max-per-minute:10}") int publicOrdersMaxPerMinute) {
        this.rateLimiter = rateLimiter;
        this.maxPerMinute = maxPerMinute;
        this.publicOrdersMaxPerMinute = publicOrdersMaxPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI()
                .replaceAll("^/api", "/api")
                .replaceAll("/+$", "");
        if ("POST".equalsIgnoreCase(request.getMethod()) && path.startsWith(PUBLIC_ORDERS_PREFIX)) {
            return false;
        }
        for (String protectedPath : PROTECTED_PATHS) {
            if (protectedPath.equals(path)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().replaceAll("/+$", "");
        String key = clientIp(request) + "|" + path;

        boolean publicOrders = path.startsWith(PUBLIC_ORDERS_PREFIX);
        int limit = publicOrders ? publicOrdersMaxPerMinute : maxPerMinute;

        if (rateLimiter.isLimited(key, limit)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    "{\"status\":429,\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"Demasiadas solicitudes. Intenta de nuevo en un minuto.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}