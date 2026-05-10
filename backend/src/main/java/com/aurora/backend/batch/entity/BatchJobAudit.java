package com.aurora.backend.batch.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "batch_job_audit")
public class BatchJobAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_name", nullable = false, length = 120)
    private String jobName;

    @Column(name = "execution_id")
    private Long executionId;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(length = 1000)
    private String message;

    protected BatchJobAudit() {
    }

    public BatchJobAudit(String jobName, Long executionId, String status, String message) {
        this.jobName = jobName;
        this.executionId = executionId;
        this.status = status;
        this.message = message;
    }

    @PrePersist
    void prePersist() {
        startedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getJobName() {
        return jobName;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getMessage() {
        return message;
    }

    public void complete(String status, String message) {
        this.status = status;
        this.message = message;
        this.finishedAt = Instant.now();
    }
}
