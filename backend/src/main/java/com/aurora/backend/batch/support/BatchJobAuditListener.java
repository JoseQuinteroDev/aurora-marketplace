package com.aurora.backend.batch.support;

import java.util.stream.Collectors;

import com.aurora.backend.batch.entity.BatchJobAudit;
import com.aurora.backend.batch.repository.BatchJobAuditRepository;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

@Component
public class BatchJobAuditListener implements JobExecutionListener {

    private final BatchJobAuditRepository batchJobAuditRepository;

    public BatchJobAuditListener(BatchJobAuditRepository batchJobAuditRepository) {
        this.batchJobAuditRepository = batchJobAuditRepository;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        batchJobAuditRepository.save(new BatchJobAudit(
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getId(),
                "STARTED",
                "Job execution started."
        ));
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        BatchJobAudit audit = batchJobAuditRepository.findTopByExecutionIdOrderByStartedAtDesc(jobExecution.getId())
                .orElseGet(() -> new BatchJobAudit(
                        jobExecution.getJobInstance().getJobName(),
                        jobExecution.getId(),
                        "STARTED",
                        "Job execution started."
                ));

        String message = jobExecution.getAllFailureExceptions().isEmpty()
                ? "Job execution completed."
                : jobExecution.getAllFailureExceptions().stream()
                        .map(Throwable::getMessage)
                        .collect(Collectors.joining("; "));

        audit.complete(jobExecution.getStatus().name(), message);
        batchJobAuditRepository.save(audit);
    }
}
