package com.kmbank.modules.account.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recent_recipients",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_recent_recipients_user_account",
           columnNames = {"user_id", "recipient_account_number"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RecentRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "recipient_account_number", nullable = false, length = 50)
    private String recipientAccountNumber;

    @Column(name = "transfer_count", nullable = false)
    @Builder.Default
    private Integer transferCount = 1;

    @Column(name = "last_transfer_at", nullable = false)
    private Instant lastTransferAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}