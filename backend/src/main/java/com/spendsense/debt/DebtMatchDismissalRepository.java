package com.spendsense.debt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DebtMatchDismissalRepository extends JpaRepository<DebtMatchDismissal, UUID> {

    @Query("SELECT d.transaction.id FROM DebtMatchDismissal d WHERE d.user.id = :userId AND d.debt.id = :debtId")
    List<UUID> findDismissedTransactionIdsByUserIdAndDebtId(
            @Param("userId") UUID userId,
            @Param("debtId") UUID debtId);

    Optional<DebtMatchDismissal> findByUserIdAndDebtIdAndTransactionId(UUID userId, UUID debtId, UUID transactionId);

    boolean existsByUserIdAndDebtIdAndTransactionId(UUID userId, UUID debtId, UUID transactionId);
}
