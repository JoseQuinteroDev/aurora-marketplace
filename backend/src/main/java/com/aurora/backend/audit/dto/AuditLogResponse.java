package com.aurora.backend.audit.dto;

import java.time.Instant;
import java.util.UUID;

import com.aurora.backend.audit.entity.AuditEventType;
import com.aurora.backend.audit.entity.AuditLog;

public record AuditLogResponse(
        UUID id,
        AuditEventType eventType,
        UUID actorUserId,
        String actorEmail,
        String entityType,
        UUID entityId,
        String message,
        Instant createdAt
) {

    public static AuditLogResponse from(AuditLog auditLog) {
        UUID actorUserId = auditLog.getActor() == null ? null : auditLog.getActor().getId();
        String actorEmail = auditLog.getActor() == null ? null : auditLog.getActor().getEmail();

        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getEventType(),
                actorUserId,
                actorEmail,
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getMessage(),
                auditLog.getCreatedAt()
        );
    }
}
