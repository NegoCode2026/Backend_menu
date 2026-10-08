package com.menusaas.modifiers.service;

import com.menusaas.modifiers.dto.ModifierDtos.*;
import com.menusaas.modifiers.entity.Modifier;
import com.menusaas.modifiers.entity.ModifierGroup;
import com.menusaas.modifiers.entity.ProductModifierGroup;
import com.menusaas.modifiers.repository.*;
import com.menusaas.products.service.ProductService;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Alta y edición de grupos de opciones, y su asociación a productos. */
@Service
@RequiredArgsConstructor
public class ModifierAdminService {

    private final ModifierGroupRepository groupRepository;
    private final ModifierRepository modifierRepository;
    private final ProductModifierGroupRepository productGroupRepository;
    private final ProductService productService;

    @Transactional(readOnly = true)
    public List<GroupResponse> listGroups() {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        List<ModifierGroup> groups = groupRepository
                .findByRestaurantIdAndActiveTrueOrderByPositionAsc(restaurantId);
        if (groups.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Modifier>> byGroup = modifierRepository
                .findByRestaurantIdAndGroupIdInOrderByPositionAsc(restaurantId,
                        groups.stream().map(ModifierGroup::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(Modifier::getGroupId));
        return groups.stream()
                .map(g -> GroupResponse.from(g, byGroup.getOrDefault(g.getId(), List.of())))
                .toList();
    }

    /**
     * Grupos con sus opciones para varios productos de una vez.
     *
     * <p>Existe para que quien arma el menú público no toque repositorios de
     * modifiers: lo resuelve por su servicio, como manda la regla de módulos.
     * Se llama una vez por menú, no por producto.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<GroupResponse>> groupsForProducts(Long restaurantId, List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<ProductModifierGroup> links = productGroupRepository
                .findByRestaurantIdAndProductIdInOrderByPositionAsc(restaurantId, productIds);
        if (links.isEmpty()) {
            return Map.of();
        }
        List<ModifierGroup> groups = groupRepository
                .findByRestaurantIdAndActiveTrueOrderByPositionAsc(restaurantId);
        Map<Long, ModifierGroup> byId = groups.stream()
                .collect(java.util.stream.Collectors.toMap(ModifierGroup::getId, g -> g));
        Map<Long, List<Modifier>> optionsByGroup = modifierRepository
                .findByRestaurantIdAndGroupIdInOrderByPositionAsc(restaurantId,
                        groups.stream().map(ModifierGroup::getId).toList())
                .stream()
                .filter(Modifier::isActive)
                .collect(java.util.stream.Collectors.groupingBy(Modifier::getGroupId));

        Map<Long, List<GroupResponse>> out = new LinkedHashMap<>();
        for (ProductModifierGroup link : links) {
            ModifierGroup group = byId.get(link.getGroupId());
            if (group == null) {
                continue;
            }
            List<OptionResponse> options = optionsByGroup.getOrDefault(group.getId(), List.of())
                    .stream().map(OptionResponse::from).toList();
            if (options.isEmpty()) {
                continue;
            }
            out.computeIfAbsent(link.getProductId(), k -> new ArrayList<>())
                    .add(GroupResponse.from(group, optionsByGroup.getOrDefault(group.getId(), List.of())));
        }
        return out;
    }

    /** Grupos que ofrece un producto: es lo que el cliente ve al pedir. */
    @Transactional(readOnly = true)
    public List<GroupResponse> groupsForProduct(Long productId) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        // Valida pertenencia antes de tocar nada.
        productService.getByIdAndRestaurantIdOrThrow(productId, restaurantId);

        List<ModifierGroup> groups = productGroupRepository
                .findByRestaurantIdAndProductIdOrderByPositionAsc(restaurantId, productId)
                .stream()
                .map(l -> groupRepository.findById(l.getGroupId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(ModifierGroup::isActive)
                .toList();
        if (groups.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Modifier>> byGroup = modifierRepository
                .findByRestaurantIdAndGroupIdInOrderByPositionAsc(restaurantId,
                        groups.stream().map(ModifierGroup::getId).toList())
                .stream()
                .filter(Modifier::isActive)
                .collect(Collectors.groupingBy(Modifier::getGroupId));
        return groups.stream()
                .map(g -> GroupResponse.from(g, byGroup.getOrDefault(g.getId(), List.of())))
                .toList();
    }

    @Transactional
    public GroupResponse createGroup(GroupRequest request) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        String name = requireName(request.name());
        int min = request.minSelections() == null ? 0 : request.minSelections();
        int max = request.maxSelections() == null ? 1 : request.maxSelections();
        validateSelectionRange(min, max);

        ModifierGroup group = ModifierGroup.builder()
                .restaurantId(restaurantId)
                .name(name)
                .description(request.description())
                .minSelections(min)
                .maxSelections(max)
                .required(Boolean.TRUE.equals(request.required()) || min > 0)
                .position(request.position() == null ? 0 : request.position())
                .build();
        return GroupResponse.from(groupRepository.save(group), List.of());
    }

    @Transactional
    public GroupResponse updateGroup(Long groupId, GroupRequest request) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        ModifierGroup group = groupRepository.findByIdAndRestaurantId(groupId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo de opciones no encontrado"));

        if (request.name() != null) {
            group.setName(requireName(request.name()));
        }
        if (request.description() != null) {
            group.setDescription(request.description());
        }
        if (request.minSelections() != null || request.maxSelections() != null) {
            int min = request.minSelections() == null ? group.getMinSelections() : request.minSelections();
            int max = request.maxSelections() == null ? group.getMaxSelections() : request.maxSelections();
            validateSelectionRange(min, max);
            group.setMinSelections(min);
            group.setMaxSelections(max);
        }
        if (request.required() != null) {
            group.setRequired(request.required());
        }
        if (request.position() != null) {
            group.setPosition(request.position());
        }
        group.setUpdatedAt(java.time.Instant.now());
        groupRepository.save(group);
        return GroupResponse.from(group,
                modifierRepository.findByGroupIdOrderByPositionAsc(groupId));
    }

    @Transactional
    public void deleteGroup(Long groupId) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        ModifierGroup group = groupRepository.findByIdAndRestaurantId(groupId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo de opciones no encontrado"));
        modifierRepository.deleteByGroupId(groupId);
        groupRepository.delete(group);
    }

    @Transactional
    public OptionResponse addOption(Long groupId, OptionRequest request) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        ModifierGroup group = groupRepository.findByIdAndRestaurantId(groupId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo de opciones no encontrado"));

        Modifier modifier = Modifier.builder()
                .restaurantId(restaurantId)
                .groupId(groupId)
                .name(requireName(request.name()))
                .priceDelta(request.priceDelta() == null ? java.math.BigDecimal.ZERO : request.priceDelta())
                .position(request.position() == null ? 0 : request.position())
                .build();
        return OptionResponse.from(modifierRepository.save(modifier));
    }

    @Transactional
    public void deleteOption(Long modifierId) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        Modifier modifier = modifierRepository.findById(modifierId)
                .filter(m -> m.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new ResourceNotFoundException("Opción no encontrada"));
        // Baja lógica: si aparece en un pedido viejo, el nombre y el precio ya
        // están congelados en order_item_modifiers, así que no se pierde nada.
        modifier.setActive(false);
        modifier.setUpdatedAt(java.time.Instant.now());
        modifierRepository.save(modifier);
    }

    @Transactional
    public List<GroupResponse> assignToProduct(Long productId, List<Long> groupIds) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        productService.getByIdAndRestaurantIdOrThrow(productId, restaurantId);

        // Reemplaza la asociación completa: lo que el admin ve es lo que hay.
        productGroupRepository.deleteByProductId(productId);
        productGroupRepository.flush();
        int position = 0;
        for (Long groupId : groupIds == null ? List.<Long>of() : groupIds) {
            ModifierGroup group = groupRepository.findByIdAndRestaurantId(groupId, restaurantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Grupo de opciones no encontrado"));
            productGroupRepository.save(ProductModifierGroup.builder()
                    .restaurantId(restaurantId)
                    .productId(productId)
                    .groupId(group.getId())
                    .position(position++)
                    .build());
        }
        return groupsForProduct(productId);
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("El nombre es obligatorio");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 120) {
            throw new BadRequestException("El nombre no puede superar 120 caracteres");
        }
        return trimmed;
    }

    private void validateSelectionRange(int min, int max) {
        if (min < 0) {
            throw new BadRequestException("El mínimo de opciones no puede ser negativo");
        }
        if (max < min) {
            throw new BadRequestException("El máximo no puede ser menor que el mínimo");
        }
        if (max > 50) {
            throw new BadRequestException("El máximo no puede superar 50 opciones");
        }
    }
}
