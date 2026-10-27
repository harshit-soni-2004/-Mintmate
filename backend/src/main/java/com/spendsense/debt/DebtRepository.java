package com.spendsense.debt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DebtRepository extends JpaRepository<Debt, UUID> {

    List<Debt> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Debt> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, DebtStatus status);

    Optional<Debt> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM Debt d WHERE d.user.id = :userId AND d.debtType = :debtType AND d.status = 'ACTIVE'")
    BigDecimal sumActiveAmountByUserIdAndDebtType(
            @Param("userId") UUID userId,
            @Param("debtType") DebtType debtType);

    @Query("SELECT d FROM Debt d WHERE d.user.id = :userId AND d.status = 'ACTIVE' AND d.dueDate IS NOT NULL AND d.dueDate < :currentDate ORDER BY d.dueDate ASC")
    List<Debt> findOverdueDebts(
            @Param("userId") UUID userId,
            @Param("currentDate") LocalDate currentDate);

    @Query("SELECT d FROM Debt d WHERE d.user.id = :userId AND d.status = 'ACTIVE' AND d.dueDate IS NOT NULL AND d.dueDate >= :currentDate ORDER BY d.dueDate ASC")
    List<Debt> findUpcomingDebts(
            @Param("userId") UUID userId,
            @Param("currentDate") LocalDate currentDate);

    @Query("SELECT d FROM Debt d WHERE d.user.id = :userId AND d.status = 'ACTIVE' AND d.amount = :amount")
    List<Debt> findActiveByUserIdAndAmount(
            @Param("userId") UUID userId,
            @Param("amount") BigDecimal amount);

    @Query("SELECT d.linkedTransaction.id FROM Debt d WHERE d.user.id = :userId AND d.linkedTransaction IS NOT NULL")
    List<UUID> findLinkedTransactionIdsByUserId(@Param("userId") UUID userId);
}
