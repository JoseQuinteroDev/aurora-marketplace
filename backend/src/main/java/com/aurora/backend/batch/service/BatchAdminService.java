package com.aurora.backend.batch.service;

import java.util.List;
import java.util.Map;

import com.aurora.backend.batch.dto.BatchJobExecutionResponse;
import com.aurora.backend.batch.dto.BatchJobInfoResponse;
import com.aurora.backend.batch.repository.BatchJobAuditRepository;
import com.aurora.backend.common.exception.NotFoundException;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchAdminService {

    private final JobLauncher jobLauncher;
    private final Map<String, Job> jobs;
    private final BatchJobAuditRepository batchJobAuditRepository;

    public BatchAdminService(
            JobLauncher jobLauncher,
            Map<String, Job> jobs,
            BatchJobAuditRepository batchJobAuditRepository
    ) {
        this.jobLauncher = jobLauncher;
        this.jobs = jobs;
        this.batchJobAuditRepository = batchJobAuditRepository;
    }

    public List<BatchJobInfoResponse> listJobs() {
        return List.of(
                new BatchJobInfoResponse("importProductsJob", "Imports products, categories, brands and variants from CSV."),
                new BatchJobInfoResponse("syncInventoryJob", "Synchronizes variant stock from CSV."),
                new BatchJobInfoResponse("cleanAbandonedCartsJob", "Deletes old empty carts.")
        );
    }

    public BatchJobExecutionResponse runJob(String jobName) throws Exception {
        Job job = jobs.get(jobName);

        if (job == null) {
            throw new NotFoundException("Batch job", jobName);
        }

        Long executionId = jobLauncher.run(
                job,
                new JobParametersBuilder()
                        .addLong("requestedAt", System.currentTimeMillis())
                        .toJobParameters()
        ).getId();

        return batchJobAuditRepository.findTopByExecutionIdOrderByStartedAtDesc(executionId)
                .map(BatchJobExecutionResponse::from)
                .orElseThrow(() -> new NotFoundException("Batch job execution", executionId));
    }

    @Transactional(readOnly = true)
    public List<BatchJobExecutionResponse> listExecutions() {
        return batchJobAuditRepository.findAllByOrderByStartedAtDesc().stream()
                .map(BatchJobExecutionResponse::from)
                .toList();
    }
}
