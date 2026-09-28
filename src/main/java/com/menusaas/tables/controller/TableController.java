package com.menusaas.tables.controller;

import com.menusaas.shared.api.ApiResponse;
import com.menusaas.tables.dto.CreateTableRequest;
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

@Tag(name = "Tables", description = "Mesas del salón (siempre del restaurante del JWT)")
@RestController
@RequestMapping("/api/tables")
@PreAuthorize("hasAnyRole('RESTAURANT_ADMIN','RESTAURANT_USER','WAITER','CASHIER')")
@RequiredArgsConstructor
public class TableController {

    private final TableService tableService;

    @Operation(summary = "Listar mis mesas")
    @GetMapping
    public ApiResponse<List<TableResponse>> list() {
        return ApiResponse.ok(tableService.listMine());
    }

    @Operation(summary = "Crear mesa")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TableResponse> create(@Valid @RequestBody CreateTableRequest request) {
        return ApiResponse.ok("Mesa creada", tableService.createMine(request));
    }

    @Operation(summary = "Eliminar mesa")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        tableService.deleteMine(id);
        return ApiResponse.ok("Mesa eliminada");
    }
}
