package com.menusaas.tables.controller;

import com.menusaas.shared.api.ApiResponse;
import com.menusaas.tables.dto.TableResolveResponse;
import com.menusaas.tables.service.TableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Public Tables", description = "Validación del código QR de mesa (sin autenticación)")
@RestController
@RequestMapping("/api/public/tables")
@RequiredArgsConstructor
public class PublicTableController {

    private final TableService tableService;

    @Operation(summary = "Validar el ?t={code} del QR: devuelve mesa real y restaurante al que pertenece")
    @GetMapping("/resolve")
    public ApiResponse<TableResolveResponse> resolve(@RequestParam UUID code) {
        return ApiResponse.ok(tableService.resolveByCode(code));
    }
}
