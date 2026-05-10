package com.aurora.backend.batch.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.batch.entity.BatchJobAudit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchJobAuditRepository extends JpaRepository<BatchJobAudit, UUID> {

    Optional<BatchJobAudit> findTopByExecutionIdOrderByStartedAtDesc(Long executionId);

    List<BatchJobAudit> findAllByOrderByStartedAtDesc();
}
