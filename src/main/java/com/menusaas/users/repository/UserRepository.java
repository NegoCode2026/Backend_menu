package com.menusaas.users.repository;

import com.menusaas.users.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("""
            select u from User u
            join fetch u.role
            left join fetch u.restaurant
            where u.restaurant.id = :restaurantId
              and (:roleName is null or u.role.name = :roleName)
            order by u.name
            """)
    List<User> findByRestaurantId(Long restaurantId, String roleName);

    long countByRestaurantId(Long restaurantId);

    /**
     * Conteo de usuarios agrupado por restaurante (panel admin: evita N+1).
     */
    @Query("select u.restaurant.id, count(u) from User u where u.restaurant.id in :ids group by u.restaurant.id")
    List<Object[]> countGroupedByRestaurantIds(@Param("ids") Collection<Long> ids);

    /**
     * Usuarios con un rol dado en varios restaurantes (panel admin: evita N+1).
     */
    @Query("select u from User u join fetch u.role where u.restaurant.id in :ids and u.role.name = :role")
    List<User> findByRestaurantIdsAndRole(@Param("ids") Collection<Long> ids, @Param("role") String role);

    @Query("""
            select u from User u
            join fetch u.role
            left join fetch u.restaurant
            where (:role is null or :role = '' or u.role.name = :role)
              and (:active is null or u.active = :active)
              and (:search is null or :search = ''
                or lower(u.name) like lower(concat('%', :search, '%'))
                or lower(u.email) like lower(concat('%', :search, '%')))
            """)
    Page<User> search(@Param("search") String search,
                      @Param("role") String role,
                      @Param("active") Boolean active,
                      Pageable pageable);

    @Query("select count(u) from User u where u.role.name = 'SUPER_ADMIN' and u.active = true")
    long countActiveSuperAdmins();

    @Query("select count(u) from User u where u.role.name = 'SUPER_ADMIN' and u.active = true and u.id <> :excludeId")
    long countOtherActiveSuperAdmins(@Param("excludeId") Long excludeId);
}