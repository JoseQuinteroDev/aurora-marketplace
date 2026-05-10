package com.aurora.backend.audit.service;

import java.util.List;
import java.util.UUID;

import com.aurora.backend.audit.dto.AuditLogResponse;
import com.aurora.backend.audit.entity.AuditEventType;
import com.aurora.backend.audit.entity.AuditLog;
import com.aurora.backend.audit.repository.AuditLogRepository;
import com.aurora.backend.user.entity.User;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void log(AuditEventType eventType, User actor, String entityType, UUID entityId, String message) {
        auditLogRepository.save(new AuditLog(eventType, actor, entityType, entityId, message));
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> listAuditLogs() {
        return auditLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(AuditLogResponse::from)
                .toList();
    }
}
