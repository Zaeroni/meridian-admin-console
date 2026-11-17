package com.meridian.admin.web;

import com.meridian.admin.service.DashboardService;
import com.meridian.admin.web.Dtos.OverviewDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/overview")
    @Operation(summary = "Pharmacies by status, orders per day, and pharmacies that need attention")
    public OverviewDto overview() {
        return dashboard.overview();
    }
}
