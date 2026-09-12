package com.kmbank.modules.account.repository;

import com.kmbank.modules.account.entity.RecentRecipient;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecentRecipientRepository extends JpaRepository<RecentRecipient, UUID> {

    List<RecentRecipient> findByUserIdOrderByLastTransferAtDesc(UUID userId, Pageable pageable);

    Optional<RecentRecipient> findByUserIdAndRecipientAccountNumber(UUID userId, String accountNumber);

    @Modifying
    @Query("""
        UPDATE RecentRecipient r
        SET r.transferCount = r.transferCount + 1,
            r.lastTransferAt = :now
        WHERE r.userId = :userId AND r.recipientAccountNumber = :accountNumber
        """)
    int incrementTransferCount(@Param("userId") UUID userId,
                               @Param("accountNumber") String accountNumber,
                               @Param("now") Instant now);
}