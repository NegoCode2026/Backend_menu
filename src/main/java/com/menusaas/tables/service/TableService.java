package com.menusaas.tables.service;

import com.menusaas.permissions.Permissions;
import com.menusaas.permissions.service.PermissionService;
import com.menusaas.shared.api.ConflictException;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
import com.menusaas.tables.dto.CreateTableRequest;
import com.menusaas.tables.dto.TableResponse;
import com.menusaas.tables.entity.RestaurantTable;
import com.menusaas.tables.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Mesas del salón: las crea/borra el equipo con permiso de edición y las
 * ven todos los roles operativos. El número es único por restaurante.
 */
@Service
@RequiredArgsConstructor
public class TableService {

    private final RestaurantTableRepository tableRepository;
    private final PermissionService permissions;

    @Transactional(readOnly = true)
    public List<TableResponse> listMine() {
        return tableRepository.findByRestaurantIdOrderByIdAsc(SecurityUtils.currentRestaurantId())
                .stream()
                .map(TableResponse::from)
                .toList();
    }

    /**
     * Números de mesa para el menú público y la validación de pedidos.
     * Consulta explícita por tenant (sin SecurityUtils): el menú público
     * no tiene sesión.
     */
    @Transactional(readOnly = true)
    public List<String> findNumbersByRestaurantId(Long restaurantId) {
        return tableRepository.findByRestaurantIdOrderByIdAsc(restaurantId)
                .stream()
                .map(RestaurantTable::getNumber)
                .toList();
    }

    /**
     * ¿La etiqueta corresponde a una mesa registrada? Compara por número
     * (ignora ceros a la izquierda: "01" = "1") o por texto exacto
     * ("Terraza" = "terraza").
     */
    public static boolean matchesKnownTable(List<String> known, String label) {
        if (known == null || known.isEmpty() || label == null || label.isBlank()) return false;
        String norm = label.trim();
        String digits = digitsOf(norm);
        for (String k : known) {
            if (k == null) continue;
            if (k.equalsIgnoreCase(norm)) return true;
            String kd = digitsOf(k);
            if (!digits.isEmpty() && !kd.isEmpty() && digits.equals(kd)) return true;
        }
        return false;
    }

    private static String digitsOf(String s) {
        String d = s.replaceAll("\\D", "");
        if (d.isEmpty()) return "";
        String stripped = d.replaceFirst("^0+", "");
        return stripped.isEmpty() ? "0" : stripped;
    }

    @Transactional
    public TableResponse createMine(CreateTableRequest request) {
        permissions.require(Permissions.ORDERS_EDIT);
        Long restaurantId = SecurityUtils.currentRestaurantId();
        String number = request.number().trim();
        if (tableRepository.existsByRestaurantIdAndNumber(restaurantId, number)) {
            throw new ConflictException("Ya existe la mesa " + number);
        }
        int seats = request.seats() == null ? 2 : Math.min(20, Math.max(1, request.seats()));
        RestaurantTable saved = tableRepository.save(RestaurantTable.builder()
                .restaurantId(restaurantId)
                .number(number)
                .seats(seats)
                .build());
        return TableResponse.from(saved);
    }

    @Transactional
    public void deleteMine(Long id) {
        permissions.require(Permissions.ORDERS_EDIT);
        RestaurantTable table = tableRepository.findByIdAndRestaurantId(id, SecurityUtils.currentRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada"));
        tableRepository.delete(table);
    }
}
