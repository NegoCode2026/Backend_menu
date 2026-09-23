package com.menusaas.reports.controller;

import com.menusaas.reports.dto.ProfitsResponse;
import com.menusaas.reports.service.ProfitReportService;
import com.menusaas.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "Reports", description = "Ganancias por pedidos entregados (tenant-scoped)")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ProfitReportService profitReportService;

    @Operation(summary = "Utilidades del día/semana/mes (?period=day|week|month&date=YYYY-MM-DD)")
    @GetMapping("/profits")
    public ApiResponse<ProfitsResponse> profits(
            @RequestParam(defaultValue = "day") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(profitReportService.getProfits(period, date));
    }
}
