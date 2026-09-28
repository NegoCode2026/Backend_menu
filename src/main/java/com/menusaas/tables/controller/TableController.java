package com.menusaas.tables.controller;

import com.menusaas.shared.api.ApiResponse;
import com.menusaas.tables.dto.TableRequest;
import com.menusaas.tables.dto.TableResponse;
import com.menusaas.tables.service.TableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Tables", description = "Gestión de mesas del restaurante")
@RestController
@RequestMapping("/api/tables")
@RequiredArgsConstructor
public class TableController {

    private final TableService tableService;

    @Operation(summary = "Listar mesas del restaurante")
    @GetMapping
    public ApiResponse<List<TableResponse>> list() {
        return ApiResponse.ok(tableService.listMine());
    }

    @Operation(summary = "Crear nueva mesa")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TableResponse> create(@Valid @RequestBody TableRequest request) {
        return ApiResponse.ok("Mesa creada", tableService.createMine(request));
    }

    @Operation(summary = "Eliminar mesa por ID")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        tableService.deleteMine(id);
        return ApiResponse.ok("Mesa eliminada");
    }
}
