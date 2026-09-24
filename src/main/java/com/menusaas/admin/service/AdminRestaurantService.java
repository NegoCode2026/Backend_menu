package com.menusaas.admin.service;

import com.menusaas.admin.dto.AdminCreateRestaurantRequest;
import com.menusaas.admin.dto.AdminRestaurantResponse;
import com.menusaas.products.repository.ProductRepository;
import com.menusaas.restaurants.entity.Restaurant;
import com.menusaas.restaurants.repository.RestaurantRepository;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.api.ConflictException;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SignedUrlService;
import com.menusaas.subscriptions.entity.Plan;
import com.menusaas.subscriptions.entity.Subscription;
import com.menusaas.subscriptions.repository.PlanRepository;
import com.menusaas.subscriptions.repository.SubscriptionRepository;
import com.menusaas.users.entity.Role;
import com.menusaas.users.entity.User;
import com.menusaas.users.repository.RoleRepository;
import com.menusaas.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gestión de restaurantes a nivel plataforma (SUPER_ADMIN).
 * Excepción legítima al acceso cross-módulo: crea restaurante + usuario
 * + suscripción en una sola transacción de aprovisionamiento.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SignedUrlService signedUrlService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<AdminRestaurantResponse> listRestaurants(String search, Boolean active, Pageable pageable) {
        Page<Restaurant> page = restaurantRepository.search(search, active, pageable);
        List<Long> ids = page.getContent().stream().map(Restaurant::getId).toList();
        if (ids.isEmpty()) {
            return page.map(r -> AdminRestaurantResponse.from(r, null, 0L, 0L, "Sin plan", "N/A"));
        }

        // Consultas agregadas por lote: 5 queries en total, sin N+1.
        Map<Long, Long> userCounts = toCountMap(userRepository.countGroupedByRestaurantIds(ids));
        Map<Long, Long> productCounts = toCountMap(productRepository.countGroupedByRestaurantIds(ids));
        Map<Long, String> adminEmails = userRepository
                .findByRestaurantIdsAndRole(ids, Role.RESTAURANT_ADMIN).stream()
                .filter(u -> u.getRestaurant() != null)
                .collect(Collectors.toMap(
                        u -> u.getRestaurant().getId(), User::getEmail, (a, b) -> a));
        Map<Long, Subscription> activeSubs = subscriptionRepository
                .findByRestaurantIdsAndStatusOrderByCreatedAtDesc(ids, Subscription.STATUS_ACTIVE).stream()
                .collect(Collectors.toMap(
                        Subscription::getRestaurantId, s -> s, (a, b) -> a));
        Map<Long, String> planNames = planRepository.findAllById(
                        activeSubs.values().stream().map(Subscription::getPlanId).toList()).stream()
                .collect(Collectors.toMap(Plan::getId, Plan::getName));

        return page.map(r -> AdminRestaurantResponse.from(
                r,
                signedUrlService.toSignedUrlOrNull(r.getLogoUrl()),
                userCounts.getOrDefault(r.getId(), 0L),
                productCounts.getOrDefault(r.getId(), 0L),
                planNameOf(r.getId(), activeSubs, planNames),
                adminEmails.getOrDefault(r.getId(), "N/A")
        ));
    }

    private static Map<Long, Long> toCountMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(
                row -> (Long) row[0], row -> (Long) row[1], (a, b) -> a));
    }

    private static String planNameOf(
            Long restaurantId, Map<Long, Subscription> activeSubs, Map<Long, String> planNames) {
        Subscription sub = activeSubs.get(restaurantId);
        if (sub == null) {
            return "Sin plan";
        }
        return planNames.getOrDefault(sub.getPlanId(), "Sin plan");
    }

    /** Compat con callers antiguos: página grande sin filtros. */
    @Transactional(readOnly = true)
    public List<AdminRestaurantResponse> listRestaurants() {
        return listRestaurants(null, null, Pageable.ofSize(100)).getContent();
    }

    @CacheEvict(value = "adminStats", allEntries = true)
    @Transactional
    public AdminRestaurantResponse createRestaurant(AdminCreateRestaurantRequest request) {
        String email = request.adminEmail().trim().toLowerCase();
        String slug = request.slug().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Ya existe una cuenta con este correo");
        }
        if (restaurantRepository.existsBySlug(slug)) {
            throw new ConflictException("El slug '" + slug + "' ya está en uso");
        }

        Role role = roleRepository.findByName(Role.RESTAURANT_ADMIN)
                .orElseThrow(() -> new IllegalStateException("Rol RESTAURANT_ADMIN no configurado"));

        String planCode = (request.planCode() != null && !request.planCode().isBlank())
                ? request.planCode().trim()
                : "NEGOCODE";
        Plan plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new BadRequestException("Plan inexistente: " + planCode));

        Restaurant restaurant = Restaurant.builder()
                .name(request.restaurantName().trim())
                .slug(slug)
                .active(true)
                .build();
        restaurant = restaurantRepository.save(restaurant);

        User user = User.builder()
                .name(request.adminName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.adminPassword()))
                .role(role)
                .restaurant(restaurant)
                .active(true)
                .build();
        userRepository.save(user);

        subscriptionRepository.save(Subscription.builder()
                .restaurantId(restaurant.getId())
                .planId(plan.getId())
                .status(Subscription.STATUS_ACTIVE)
                .provider(Subscription.PROVIDER_MANUAL)
                .startsAt(Instant.now())
                .build());

        log.info("Super Admin creó restaurante: slug={}, admin={}", slug, email);
        auditService.log("RESTAURANT_CREATED", "restaurant", restaurant.getId(),
                "slug=" + slug + ", admin=" + email + ", plan=" + plan.getCode());

        return AdminRestaurantResponse.from(
                restaurant,
                null,
                1L,
                0L,
                plan.getName(),
                email
        );
    }

    @CacheEvict(value = "adminStats", allEntries = true)
    @Transactional
    public void toggleRestaurantActive(Long id, boolean active) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado"));
        restaurant.setActive(active);
        restaurantRepository.save(restaurant);
        auditService.log(active ? "RESTAURANT_ACTIVATED" : "RESTAURANT_DEACTIVATED",
                "restaurant", id, "active=" + active);
    }
}
