package com.menusaas.admin.service;

import com.menusaas.admin.dto.AdminCreateRestaurantRequest;
import com.menusaas.admin.dto.AdminRestaurantResponse;
import com.menusaas.admin.dto.AdminStatsResponse;
import com.menusaas.admin.dto.AdminUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Fachada de compatibilidad: delega en los servicios especializados.
 *
 * @deprecated inyectar {@link AdminStatsService}, {@link AdminRestaurantService}
 * o {@link AdminUserService} directamente. Será eliminado en la próxima minor.
 */
@Deprecated(forRemoval = true)
@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminStatsService statsService;
    private final AdminRestaurantService restaurantService;
    private final AdminUserService userService;

    public AdminStatsResponse getStats() {
        return statsService.getStats();
    }

    public Page<AdminRestaurantResponse> listRestaurants(String search, Boolean active, Pageable pageable) {
        return restaurantService.listRestaurants(search, active, pageable);
    }

    public List<AdminRestaurantResponse> listRestaurants() {
        return restaurantService.listRestaurants();
    }

    public AdminRestaurantResponse createRestaurant(AdminCreateRestaurantRequest request) {
        return restaurantService.createRestaurant(request);
    }

    public void toggleRestaurantActive(Long id, boolean active) {
        restaurantService.toggleRestaurantActive(id, active);
    }

    public Page<AdminUserResponse> listUsers(String search, String role, Boolean active, Pageable pageable) {
        return userService.listUsers(search, role, active, pageable);
    }

    public List<AdminUserResponse> listUsers() {
        return userService.listUsers();
    }

    public void toggleUserActive(Long id, boolean active) {
        userService.toggleUserActive(id, active);
    }
}
