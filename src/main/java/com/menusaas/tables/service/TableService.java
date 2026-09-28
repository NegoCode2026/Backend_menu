package com.menusaas.tables.service;

import com.menusaas.shared.api.ConflictException;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
import com.menusaas.tables.dto.TableRequest;
import com.menusaas.tables.dto.TableResponse;
import com.menusaas.tables.entity.RestaurantTable;
import com.menusaas.tables.repository.TableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TableService {

    private final TableRepository tableRepository;

    @Transactional(readOnly = true)
    public List<TableResponse> listMine() {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        return tableRepository.findByRestaurantIdOrderByNumberAsc(restaurantId)
                .stream()
                .map(TableResponse::from)
                .toList();
    }

    @Transactional
    public TableResponse createMine(TableRequest request) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        String number = request.number().trim();
        if (tableRepository.existsByRestaurantIdAndNumberIgnoreCase(restaurantId, number)) {
            throw new ConflictException("Ya existe una mesa con el número " + number);
        }
        RestaurantTable table = RestaurantTable.builder()
                .restaurantId(restaurantId)
                .number(number)
                .seats(request.seats() != null ? request.seats() : 2)
                .build();
        return TableResponse.from(tableRepository.save(table));
    }

    @Transactional
    public void deleteMine(Long id) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        RestaurantTable table = tableRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa no encontrada"));
        tableRepository.delete(table);
    }
}
