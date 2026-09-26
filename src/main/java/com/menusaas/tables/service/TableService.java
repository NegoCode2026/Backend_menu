package com.menusaas.tables.service;

import com.menusaas.config.AppProperties;
import com.menusaas.restaurants.entity.Restaurant;
import com.menusaas.restaurants.service.RestaurantService;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.api.ConflictException;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
import com.menusaas.tables.dto.TableQrInfoResponse;
import com.menusaas.tables.dto.TableRequest;
import com.menusaas.tables.dto.TableResolveResponse;
import com.menusaas.tables.dto.TableResponse;
import com.menusaas.tables.entity.RestaurantTable;
import com.menusaas.tables.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Mesas del restaurante (MVP). El tenant sale siempre del JWT; lo público
 * resuelve por {@code code} UUID y valida pertenencia al restaurante.
 */
@Service
@RequiredArgsConstructor
public class TableService {

    private final RestaurantTableRepository tableRepository;
    private final RestaurantService restaurantService;
    private final AppProperties appProperties;

    @Transactional(readOnly = true)
    public List<TableResponse> listMine() {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        String slug = restaurantService.slugOrThrow(restaurantId);
        return tableRepository.findByRestaurantIdOrderByIdAsc(restaurantId)
                .stream()
                .map(t -> TableResponse.from(t, slug, buildMenuUrl(slug, t.getCode())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TableResponse getMine(Long id) {
        return toResponse(findScoped(id));
    }

    @Transactional
    public TableResponse createMine(TableRequest request) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        String label = request.label().trim();
        if (tableRepository.existsByRestaurantIdAndLabelIgnoreCase(restaurantId, label)) {
            throw new ConflictException("Ya existe una mesa con ese nombre");
        }
        RestaurantTable table = RestaurantTable.builder()
                .restaurantId(restaurantId)
                .label(label)
                .code(UUID.randomUUID())
                .build();
        return toResponse(tableRepository.save(table));
    }

    @Transactional
    public TableResponse updateMine(Long id, TableRequest request) {
        RestaurantTable table = findScoped(id);
        String label = request.label().trim();
        if (!table.getLabel().equalsIgnoreCase(label)
                && tableRepository.existsByRestaurantIdAndLabelIgnoreCase(table.getRestaurantId(), label)) {
            throw new ConflictException("Ya existe una mesa con ese nombre");
        }
        table.setLabel(label);
        return toResponse(tableRepository.save(table));
    }

    /**
     * Borrado físico (MVP sin soft-delete). Los pedidos históricos conservan
     * {@code table_number} como snapshot y su {@code table_id} pasa a NULL
     * por la FK {@code ON DELETE SET NULL}.
     */
    @Transactional
    public void deleteMine(Long id) {
        tableRepository.delete(findScoped(id));
    }

    /**
     * Genera un code nuevo (invalida los QR impresos con el anterior).
     */
    @Transactional
    public TableResponse regenerateCodeMine(Long id) {
        RestaurantTable table = findScoped(id);
        table.setCode(UUID.randomUUID());
        return toResponse(tableRepository.save(table));
    }

    /**
     * Datos para que el frontend dibuje el QR (el backend no genera imagen).
     */
    @Transactional(readOnly = true)
    public TableQrInfoResponse qrInfoMine(Long id) {
        RestaurantTable table = findScoped(id);
        String slug = restaurantService.slugOrThrow(table.getRestaurantId());
        return new TableQrInfoResponse(
                table.getId(), table.getLabel(), table.getCode(), slug, buildMenuUrl(slug, table.getCode()));
    }

    /**
     * Validación pública del ?t={code}: resuelve la mesa y accede al
     * restaurante vía RestaurantService para devolver su información.
     */
    @Transactional(readOnly = true)
    public TableResolveResponse resolveByCode(UUID code) {
        RestaurantTable table = tableRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada"));
        Restaurant restaurant = restaurantService.findActiveByIdOrThrow(table.getRestaurantId());
        return new TableResolveResponse(
                table.getId(), table.getLabel(),
                restaurant.getId(), restaurant.getSlug(), restaurant.getName(),
                buildMenuUrl(restaurant.getSlug(), table.getCode()));
    }

    // ------------------------------------------------------------------
    // Puertas para orders (con restaurantId explícito, sin SecurityUtils).
    // ------------------------------------------------------------------

    /**
     * Valida el tableCode de un pedido: existe y pertenece al restaurante del
     * slug. Lanza 404 si el code no existe, 400 si es de otro restaurante.
     */
    @Transactional(readOnly = true)
    public RestaurantTable requireBelongsToRestaurant(UUID code, Long restaurantId) {
        RestaurantTable table = tableRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada"));
        if (!table.getRestaurantId().equals(restaurantId)) {
            throw new BadRequestException("El código de mesa no pertenece a este restaurante");
        }
        return table;
    }

    private RestaurantTable findScoped(Long id) {
        return tableRepository.findByIdAndRestaurantId(id, SecurityUtils.currentRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada"));
    }

    private TableResponse toResponse(RestaurantTable table) {
        String slug = restaurantService.slugOrThrow(table.getRestaurantId());
        return TableResponse.from(table, slug, buildMenuUrl(slug, table.getCode()));
    }

    private String buildMenuUrl(String slug, UUID code) {
        String base = appProperties.appBaseUrl().replaceAll("/+$", "");
        return base + "/menu/" + slug + "?t=" + code;
    }
}
