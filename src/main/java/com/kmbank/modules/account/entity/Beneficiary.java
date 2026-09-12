package com.kmbank.modules.account.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "beneficiaries",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_beneficiaries_user_account",
           columnNames = {"user_id", "account_number"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}