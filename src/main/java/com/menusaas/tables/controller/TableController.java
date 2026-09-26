package com.menusaas.tables.controller;

import com.menusaas.shared.api.ApiResponse;
import com.menusaas.tables.dto.TableQrInfoResponse;
import com.menusaas.tables.dto.TableRequest;
import com.menusaas.tables.dto.TableResponse;
import com.menusaas.tables.service.TableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Tables", description = "Mesas del restaurante (siempre del restaurante del JWT)")
@RestController
@RequestMapping("/api/tables")
@RequiredArgsConstructor
public class TableController {

    private final TableService tableService;

    @Operation(summary = "Listar mis mesas (cada una trae code + menuUrl para dibujar su QR)")
    @GetMapping
    public ApiResponse<List<TableResponse>> list() {
        return ApiResponse.ok(tableService.listMine());
    }

    @Operation(summary = "Obtener una mesa")
    @GetMapping("/{id}")
    public ApiResponse<TableResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(tableService.getMine(id));
    }

    @Operation(summary = "Crear mesa (genera su código QR único)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissions.has('SETTINGS_EDIT')")
    public ApiResponse<TableResponse> create(@Valid @RequestBody TableRequest request) {
        return ApiResponse.ok("Mesa creada", tableService.createMine(request));
    }

    @Operation(summary = "Renombrar mesa (el código no cambia)")
    @PutMapping("/{id}")
    @PreAuthorize("@permissions.has('SETTINGS_EDIT')")
    public ApiResponse<TableResponse> update(@PathVariable Long id, @Valid @RequestBody TableRequest request) {
        return ApiResponse.ok("Mesa actualizada", tableService.updateMine(id, request));
    }

    @Operation(summary = "Eliminar mesa (los pedidos guardan el nombre como histórico)")
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissions.has('SETTINGS_EDIT')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        tableService.deleteMine(id);
        return ApiResponse.ok("Mesa eliminada");
    }

    @Operation(summary = "Regenerar el código de una mesa (invalida QRs impresos)")
    @PostMapping("/{id}/regenerate-code")
    @PreAuthorize("@permissions.has('SETTINGS_EDIT')")
    public ApiResponse<TableResponse> regenerateCode(@PathVariable Long id) {
        return ApiResponse.ok("Código regenerado", tableService.regenerateCodeMine(id));
    }

    @Operation(summary = "Datos para que el frontend dibuje el QR (code + URL, sin imagen)")
    @GetMapping("/{id}/qr-info")
    public ApiResponse<TableQrInfoResponse> qrInfo(@PathVariable Long id) {
        return ApiResponse.ok(tableService.qrInfoMine(id));
    }
}
