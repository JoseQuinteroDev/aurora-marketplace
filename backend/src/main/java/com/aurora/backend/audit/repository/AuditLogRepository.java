package com.aurora.backend.audit.repository;

import java.util.List;
import java.util.UUID;

import com.aurora.backend.audit.entity.AuditLog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findAllByOrderByCreatedAtDesc();
}
