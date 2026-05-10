package com.aurora.backend.admin.dashboard.controller;

import com.aurora.backend.admin.dashboard.dto.AdminDashboardSummaryResponse;
import com.aurora.backend.admin.dashboard.service.AdminDashboardService;
import com.aurora.backend.common.api.ApiResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping("/summary")
    public ApiResponse<AdminDashboardSummaryResponse> getSummary() {
        return ApiResponse.success("Admin dashboard summary retrieved successfully.", adminDashboardService.getSummary());
    }
}
