package com.aurora.backend.batch.dto;

import java.time.Instant;
import java.util.UUID;

import com.aurora.backend.batch.entity.BatchJobAudit;

public record BatchJobExecutionResponse(
        UUID id,
        String jobName,
        Long executionId,
        String status,
        Instant startedAt,
        Instant finishedAt,
        String message
) {

    public static BatchJobExecutionResponse from(BatchJobAudit audit) {
        return new BatchJobExecutionResponse(
                audit.getId(),
                audit.getJobName(),
                audit.getExecutionId(),
                audit.getStatus(),
                audit.getStartedAt(),
                audit.getFinishedAt(),
                audit.getMessage()
        );
    }
}
