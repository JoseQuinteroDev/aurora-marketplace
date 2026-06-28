package com.aurora.backend.audit.controller;

import java.util.List;

import com.aurora.backend.audit.dto.AuditLogResponse;
import com.aurora.backend.audit.service.AuditLogService;
import com.aurora.backend.common.api.ApiResponse;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    public AdminAuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ApiResponse<List<AuditLogResponse>> listAuditLogs() {
        return ApiResponse.success("Audit logs retrieved successfully.", auditLogService.listAuditLogs());
    }
}
