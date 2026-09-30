package com.menusaas.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menusaas.shared.api.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    /** Coincide con la ventana fija de {@link RateLimiter} (60s). */
    private static final long RETRY_AFTER_SECONDS = 60L;

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final int maxPerMinute;
    private final int publicOrdersMaxPerMinute;

    public RateLimitFilter(RateLimiter rateLimiter,
                           ObjectMapper objectMapper,
                           @Value("${app.rate-limiting.auth-max-per-minute:20}") int maxPerMinute,
                           @Value("${app.rate-limiting.public-orders-max-per-minute:10}") int publicOrdersMaxPerMinute) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
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
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            // RFC 6585: el cliente debe saber cuánto esperar.
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(RETRY_AFTER_SECONDS));
            ErrorResponse body = ErrorResponse.of(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "TOO_MANY_REQUESTS",
                    "Demasiadas solicitudes. Intenta de nuevo en un minuto.",
                    null);
            objectMapper.writeValue(response.getWriter(), body);
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