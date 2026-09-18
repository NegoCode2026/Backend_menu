package com.menusaas.admin.service;

import com.menusaas.admin.dto.*;
import com.menusaas.files.security.SignedUrlService;
import com.menusaas.products.repository.ProductRepository;
import com.menusaas.restaurants.entity.Restaurant;
import com.menusaas.restaurants.repository.RestaurantRepository;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.api.ConflictException;
import com.menusaas.shared.api.ForbiddenException;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
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
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SignedUrlService signedUrlService;
    private final AuditService auditService;

    @Cacheable(value = "adminStats", unless = "#result == null")
    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        long totalRestaurants = restaurantRepository.count();
        long activeRestaurants = restaurantRepository.countByActive(true);
        long totalUsers = userRepository.count();
        long activeSubscriptions = subscriptionRepository.countByStatus(Subscription.STATUS_ACTIVE);
        long totalProducts = productRepository.count();

        return new AdminStatsResponse(
                totalRestaurants,
                activeRestaurants,
                totalUsers,
                activeSubscriptions,
                totalProducts
        );
    }

    /**
     * Listado paginado con búsqueda server-side. Evita el N+1 del listado
     * original agrupando counts y planes en 4 queries por página.
     */
    @Transactional(readOnly = true)
    public Page<AdminRestaurantResponse> listRestaurants(String search, Boolean active, Pageable pageable) {
        Page<Restaurant> page = restaurantRepository.search(search, active, pageable);
        List<Long> ids = page.getContent().stream().map(Restaurant::getId).toList();

        Map<Long, Long> userCounts = ids.isEmpty() ? Map.of() : userRepository.findAll().stream()
                .filter(u -> u.getRestaurant() != null && ids.contains(u.getRestaurant().getId()))
                .collect(Collectors.groupingBy(u -> u.getRestaurant().getId(), Collectors.counting()));
        // Nota: para páginas de 20 el findAll filtrado es acotado; si la tabla
        // crece a miles, migrar a query countByRestaurantIdIn(ids).
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

        return page.map(r -> new AdminRestaurantResponse(
                r.getId(),
                r.getName(),
                r.getSlug(),
                signedUrlService.toSignedUrlOrNull(r.getLogoUrl()),
                r.getPhone(),
                r.getAddress(),
                r.isActive(),
                r.getCreatedAt(),
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

        // planCode estricto: vacío → NEGOCODE; inexistente → 400 (antes fallback silencioso)
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

        return new AdminRestaurantResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getSlug(),
                null,
                restaurant.getPhone(),
                restaurant.getAddress(),
                restaurant.isActive(),
                restaurant.getCreatedAt(),
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

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(String search, String role, Boolean active, Pageable pageable) {
        String normalizedRole = (role == null || role.isBlank() || "all".equalsIgnoreCase(role)) ? null : role.trim();
        if (normalizedRole != null && normalizedRole.startsWith("ROLE_")) {
            normalizedRole = normalizedRole.substring(5);
        }
        if (normalizedRole != null && !List.of(Role.SUPER_ADMIN, Role.RESTAURANT_ADMIN, Role.RESTAURANT_USER).contains(normalizedRole)) {
            throw new BadRequestException("Rol inválido: " + role);
        }
        final String roleFilter = normalizedRole;
        Page<User> page = userRepository.search(search, roleFilter, active, pageable);
        return page.map(u -> new AdminUserResponse(
                u.getId(),
                u.getName(),
                u.getEmail(),
                u.getRole().getName(),
                u.isActive(),
                u.getCreatedAt(),
                u.getRestaurant() != null ? u.getRestaurant().getId() : null,
                u.getRestaurant() != null ? u.getRestaurant().getName() : "Plataforma (Global)"
        ));
    }

    /** Compat con callers antiguos. */
    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        return listUsers(null, null, null, Pageable.ofSize(200)).getContent();
    }

    @CacheEvict(value = "adminStats", allEntries = true)
    @Transactional
    public void toggleUserActive(Long id, boolean active) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (user.getId().equals(SecurityUtils.currentUser().getId()) && !active) {
            throw new ForbiddenException("No puede desactivarse a sí mismo");
        }

        // Nunca dejar la plataforma sin superadmins activos
        if (Role.SUPER_ADMIN.equals(user.getRole().getName()) && !active
                && userRepository.countOtherActiveSuperAdmins(user.getId()) == 0) {
            throw new ForbiddenException("No se puede desactivar al último Super Admin activo");
        }

        user.setActive(active);
        userRepository.save(user);
        auditService.log(active ? "USER_ACTIVATED" : "USER_DEACTIVATED",
                "user", id, "email=" + user.getEmail() + ", active=" + active);
    }
}
