package com.kmbank.modules.transaction.entity;

import com.kmbank.modules.transaction.enums.TransactionStatus;
import com.kmbank.modules.transaction.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Nullable — deposit không có source account.
     */
    @Column(name = "source_account_id")
    private UUID sourceAccountId;

    /**
     * Nullable — withdrawal không có destination account.
     */
    @Column(name = "destination_account_id")
    private UUID destinationAccountId;

    @Column(name = "initiated_by")
    private UUID initiatedBy;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "fee", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal fee = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    @Column(name = "reference_number", nullable = false, unique = true)
    private String referenceNumber;

    @Column(name = "description")
    private String description;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Ngày kế toán — dùng cho báo cáo tài chính.
     * Có thể khác created_at (ví dụ: giao dịch đêm hạch toán vào ngày hôm sau).
     */
    @Column(name = "value_date", nullable = false)
    @Builder.Default
    private LocalDate valueDate = LocalDate.now();

    /**
     * Optional client-supplied idempotency key (UUID string, max 36 chars).
     * When present, duplicate requests with the same key return the original response
     * instead of creating a second transaction.
     * The unique constraint is enforced at the DB level; a DataIntegrityViolationException
     * on insert is caught by GlobalExceptionHandler and returned as 409.
     */
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    /**
     * Flagged for manual review by Ops/Admin during background reconciliation when an anomaly is detected.
     */
    @Column(name = "manual_review_flagged", nullable = false)
    @Builder.Default
    private Boolean manualReviewFlagged = false;
}
