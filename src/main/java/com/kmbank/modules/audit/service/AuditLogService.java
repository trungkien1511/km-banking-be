package com.kmbank.modules.audit.service;

import com.kmbank.modules.audit.entity.AuditLog;
import com.kmbank.modules.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for recording security and compliance audit logs in the {@code audit_logs} table.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Records an audit log for a successful operation.
     *
     * <p><b>Transactional propagation:</b> Intentional default {@link Propagation#REQUIRED}.
     * Joins the caller's existing transaction so that the audit entry and the business state
     * change (e.g., money transfer) commit or rollback atomically together.</p>
     *
     * <p><b>Note on ipAddress / userAgent:</b> Left {@code null} at the service layer by design
     * to keep service methods decoupled from HTTP web context. Database columns permit nulls.</p>
     *
     * @param actorUserId  the UUID of the user performing the action
     * @param action       the action name (e.g., "TRANSFER", "DEPOSIT", "WITHDRAWAL")
     * @param resourceId   the UUID of the affected resource (e.g. transaction ID)
     * @param status       the outcome status (e.g., "SUCCESS")
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void logSuccess(UUID actorUserId, String action, UUID resourceId, String status) {
        saveLog(actorUserId, action, "TRANSACTION", resourceId, status);
    }

    /**
     * Records an audit log for a failed operation.
     *
     * <p><b>Transactional propagation:</b> Uses {@link Propagation#REQUIRES_NEW} so that
     * the audit log entry is committed in its own independent transaction even when the outer
     * business transaction is rolled back.</p>
     *
     * @param actorUserId  the UUID of the user performing the action
     * @param action       the action name (e.g., "TRANSFER", "DEPOSIT", "WITHDRAWAL")
     * @param resourceId   the UUID of the affected resource
     * @param status       failure reason or status description (e.g., "FAILED")
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailed(UUID actorUserId, String action, UUID resourceId, String status) {
        saveLog(actorUserId, action, "TRANSACTION", resourceId, status != null ? status : "FAILED");
    }

    /**
     * Records an audit log for a blocked or duplicate request (e.g. idempotency replay).
     *
     * <p><b>Transactional propagation:</b> Uses {@link Propagation#REQUIRES_NEW} so that
     * security audit entries persist independently.</p>
     *
     * @param actorUserId  the UUID of the user performing the action
     * @param action       the action name (e.g., "DUPLICATE_REQUEST_BLOCKED")
     * @param resourceId   the UUID of the original transaction
     * @param status       blocked status description
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logBlocked(UUID actorUserId, String action, UUID resourceId, String status) {
        saveLog(actorUserId, action, "TRANSACTION", resourceId, status != null ? status : "BLOCKED");
    }

    private void saveLog(UUID actorUserId, String action, String resourceType, UUID resourceId, String status) {
        AuditLog auditLog = AuditLog.builder()
                .actorUserId(actorUserId)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .status(status)
                .ipAddress(null) // Intentionally null at service layer; web context decoupled
                .userAgent(null) // Intentionally null at service layer; web context decoupled
                .build();

        auditLogRepository.save(auditLog);
        log.debug("Audit log recorded: action={}, actor={}, resourceId={}, status={}",
                action, actorUserId, resourceId, status);
    }
}
