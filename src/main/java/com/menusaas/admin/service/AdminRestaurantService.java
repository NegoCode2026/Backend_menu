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

        Map<Long, Long> userCounts = ids.isEmpty() ? Map.of() : userRepository.findAll().stream()
                .filter(u -> u.getRestaurant() != null && ids.contains(u.getRestaurant().getId()))
                .collect(Collectors.groupingBy(u -> u.getRestaurant().getId(), Collectors.counting()));
        Map<Long, String> planNames = ids.stream().collect(Collectors.toMap(
                id -> id,
                id -> subscriptionRepository.findFirstByRestaurantIdAndStatusOrderByCreatedAtDesc(
                                id, Subscription.STATUS_ACTIVE)
                        .flatMap(s -> planRepository.findById(s.getPlanId()))
                        .map(Plan::getName)
                        .orElse("Sin plan")));
        Map<Long, String> adminEmails = ids.stream().collect(Collectors.toMap(
                id -> id,
                id -> userRepository.findByRestaurantId(id, Role.RESTAURANT_ADMIN)
                        .stream().findFirst().map(User::getEmail).orElse("N/A")));

        return page.map(r -> AdminRestaurantResponse.from(
                r,
                signedUrlService.toSignedUrlOrNull(r.getLogoUrl()),
                userCounts.getOrDefault(r.getId(), 0L),
                productRepository.countByRestaurantId(r.getId()),
                planNames.getOrDefault(r.getId(), "Sin plan"),
                adminEmails.getOrDefault(r.getId(), "N/A")
        ));
    }

    /** Compat con callers antiguos: página grande sin filtros. */
    @Transactional(readOnly = true)
    public List<AdminRestaurantResponse> listRestaurants() {
        return listRestaurants(null, null, Pageable.ofSize(200)).getContent();
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
