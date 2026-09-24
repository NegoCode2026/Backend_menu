package com.menusaas.cash.controller;

import com.menusaas.cash.dto.CashClosingResponse;
import com.menusaas.cash.dto.CashTodayResponse;
import com.menusaas.cash.dto.CloseCashRequest;
import com.menusaas.cash.service.CashService;
import com.menusaas.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Cash", description = "Cierre de caja diario (tenant-scoped)")
@RestController
@RequestMapping("/api/cash")
@PreAuthorize("@permissions.has('CASH_CLOSE')")
@RequiredArgsConstructor
public class CashController {

    private final CashService cashService;

    @Operation(summary = "Estado de la caja de hoy (esperado por método + cierre si existe)")
    @GetMapping("/today")
    public ApiResponse<CashTodayResponse> today() {
        return ApiResponse.ok(cashService.today());
    }

    @Operation(summary = "Cerrar la caja de hoy con el efectivo contado")
    @PostMapping("/close")
    public ApiResponse<CashClosingResponse> close(@Valid @RequestBody CloseCashRequest request) {
        return ApiResponse.ok("Caja cerrada", cashService.closeToday(request));
    }

    @Operation(summary = "Historial de cierres (?page=&size=)")
    @GetMapping("/closings")
    public ApiResponse<Page<CashClosingResponse>> history(
            @PageableDefault(size = 30, sort = "businessDate") Pageable pageable) {
        return ApiResponse.ok(cashService.history(pageable));
    }
}
