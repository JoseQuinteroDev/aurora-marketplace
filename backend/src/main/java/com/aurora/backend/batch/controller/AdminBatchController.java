package com.aurora.backend.batch.controller;

import java.util.List;

import com.aurora.backend.batch.dto.BatchJobExecutionResponse;
import com.aurora.backend.batch.dto.BatchJobInfoResponse;
import com.aurora.backend.batch.service.BatchAdminService;
import com.aurora.backend.common.api.ApiResponse;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/batch")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBatchController {

    private final BatchAdminService batchAdminService;

    public AdminBatchController(BatchAdminService batchAdminService) {
        this.batchAdminService = batchAdminService;
    }

    @GetMapping("/jobs")
    public ApiResponse<List<BatchJobInfoResponse>> listJobs() {
        return ApiResponse.success("Batch jobs retrieved successfully.", batchAdminService.listJobs());
    }

    @PostMapping("/jobs/{jobName}/run")
    public ApiResponse<BatchJobExecutionResponse> runJob(@PathVariable String jobName) throws Exception {
        return ApiResponse.success("Batch job executed.", batchAdminService.runJob(jobName));
    }

    @GetMapping("/executions")
    public ApiResponse<List<BatchJobExecutionResponse>> listExecutions() {
        return ApiResponse.success("Batch executions retrieved successfully.", batchAdminService.listExecutions());
    }
}
