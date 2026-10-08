package com.menusaas.modifiers.controller;

import com.menusaas.modifiers.dto.ModifierDtos.*;
import com.menusaas.modifiers.service.ModifierAdminService;
import com.menusaas.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Grupos de opciones de un producto ("Tamaño", "Término", "Extras").
 *
 * <p>Sin esto no se puede publicar una carta real: no había forma de vender
 * "pequeño / mediano / grande" ni "sin cebolla".
 */
@Tag(name = "Modifiers", description = "Opciones y variantes de productos")
@RestController
@RequestMapping("/api/modifiers")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('RESTAURANT_ADMIN','RESTAURANT_USER')")
public class ModifierController {

    private final ModifierAdminService service;

    @Operation(summary = "Listar grupos de opciones del restaurante")
    @GetMapping("/groups")
    public ApiResponse<List<GroupResponse>> listGroups() {
        return ApiResponse.ok(service.listGroups());
    }

    @Operation(summary = "Crear un grupo de opciones")
    @PostMapping("/groups")
    @PreAuthorize("@permissions.has('MENU_EDIT')")
    public ApiResponse<GroupResponse> createGroup(@Valid @RequestBody GroupRequest request) {
        return ApiResponse.ok("Grupo de opciones creado", service.createGroup(request));
    }

    @Operation(summary = "Actualizar un grupo de opciones")
    @PutMapping("/groups/{id}")
    @PreAuthorize("@permissions.has('MENU_EDIT')")
    public ApiResponse<GroupResponse> updateGroup(@PathVariable Long id,
                                                 @Valid @RequestBody GroupRequest request) {
        return ApiResponse.ok("Grupo de opciones actualizado", service.updateGroup(id, request));
    }

    @Operation(summary = "Eliminar un grupo de opciones y sus opciones")
    @DeleteMapping("/groups/{id}")
    @PreAuthorize("@permissions.has('MENU_EDIT')")
    public ApiResponse<Void> deleteGroup(@PathVariable Long id) {
        service.deleteGroup(id);
        return ApiResponse.ok("Grupo de opciones eliminado");
    }

    @Operation(summary = "Añadir una opción a un grupo")
    @PostMapping("/groups/{groupId}/options")
    @PreAuthorize("@permissions.has('MENU_EDIT')")
    public ApiResponse<OptionResponse> addOption(@PathVariable Long groupId,
                                                 @Valid @RequestBody OptionRequest request) {
        return ApiResponse.ok("Opción creada", service.addOption(groupId, request));
    }

    @Operation(summary = "Desactivar una opción (baja lógica)")
    @DeleteMapping("/options/{id}")
    @PreAuthorize("@permissions.has('MENU_EDIT')")
    public ApiResponse<Void> deleteOption(@PathVariable Long id) {
        service.deleteOption(id);
        return ApiResponse.ok("Opción eliminada");
    }

    @Operation(summary = "Grupos que ofrece un producto")
    @GetMapping("/product/{productId}")
    public ApiResponse<List<GroupResponse>> groupsForProduct(@PathVariable Long productId) {
        return ApiResponse.ok(service.groupsForProduct(productId));
    }

    @Operation(summary = "Asignar grupos de opciones a un producto (reemplaza la lista)")
    @PutMapping("/product/{productId}/groups")
    @PreAuthorize("@permissions.has('MENU_EDIT')")
    public ApiResponse<List<GroupResponse>> assign(@PathVariable Long productId,
                                                  @RequestBody List<Long> groupIds) {
        return ApiResponse.ok("Opciones del producto actualizadas",
                service.assignToProduct(productId, groupIds));
    }
}
