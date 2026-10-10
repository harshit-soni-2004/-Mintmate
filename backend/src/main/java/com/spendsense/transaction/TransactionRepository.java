package com.spendsense.transaction;

import com.spendsense.dashboard.dto.CategorySpend;
import com.spendsense.dashboard.dto.DailySpend;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    @Query("SELECT new com.spendsense.dashboard.dto.CategorySpend(t.category, SUM(t.amount), COUNT(t.id)) " +
           "FROM Transaction t WHERE t.user.id = :userId AND t.transactionType = :type " +
           "AND t.transactionDate >= :fromDate AND t.transactionDate <= :toDate " +
           "GROUP BY t.category ORDER BY SUM(t.amount) DESC")
    List<CategorySpend> findCategorySpending(
            @Param("userId") UUID userId,
            @Param("type") TransactionType type,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query("SELECT new com.spendsense.dashboard.dto.DailySpend(t.transactionDate, t.transactionType, SUM(t.amount), COUNT(t.id)) " +
           "FROM Transaction t WHERE t.user.id = :userId " +
           "AND t.transactionType IN (com.spendsense.transaction.TransactionType.EXPENSE, com.spendsense.transaction.TransactionType.INCOME) " +
           "AND t.transactionDate >= :fromDate AND t.transactionDate <= :toDate " +
           "GROUP BY t.transactionDate, t.transactionType ORDER BY t.transactionDate ASC")
    List<DailySpend> findDailySpending(
            @Param("userId") UUID userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId " +
           "AND t.amount = :amount " +
           "AND t.transactionDate >= :fromDate AND t.transactionDate <= :toDate " +
           "AND t.linkedTransaction IS NULL " +
           "ORDER BY t.transactionDate ASC")
    List<Transaction> findPotentialTransferCounterparts(
            @Param("userId") UUID userId,
            @Param("amount") BigDecimal amount,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.user.id = :userId AND t.transactionType = :type " +
           "AND t.transactionDate >= :fromDate AND t.transactionDate <= :toDate")
    BigDecimal sumAmountByUserIdAndTypeAndDateBetween(
            @Param("userId") UUID userId,
            @Param("type") TransactionType type,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.user.id = :userId AND t.category = :category AND t.transactionType = :type " +
           "AND t.transactionDate >= :fromDate AND t.transactionDate <= :toDate")
    BigDecimal sumAmountByUserIdAndCategoryAndTypeAndDateBetween(
            @Param("userId") UUID userId,
            @Param("category") TransactionCategory category,
            @Param("type") TransactionType type,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query("SELECT COUNT(t.id) FROM Transaction t " +
           "WHERE t.user.id = :userId AND t.category = :category AND t.transactionType = :type " +
           "AND t.transactionDate >= :fromDate AND t.transactionDate <= :toDate")
    long countByUserIdAndCategoryAndTypeAndDateBetween(
            @Param("userId") UUID userId,
            @Param("category") TransactionCategory category,
            @Param("type") TransactionType type,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    List<Transaction> findTop10ByUser_IdOrderByTransactionDateDescCreatedAtDesc(UUID userId);

    List<Transaction> findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(
            UUID userId, LocalDate fromDate, LocalDate toDate);

    @Query("SELECT MAX(t.amount) FROM Transaction t " +
           "WHERE t.user.id = :userId AND t.transactionType = :type " +
           "AND t.transactionDate >= :fromDate AND t.transactionDate <= :toDate")
    BigDecimal maxAmountByUserIdAndTypeAndDateBetween(
            @Param("userId") UUID userId,
            @Param("type") TransactionType type,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query("SELECT COUNT(t.id) FROM Transaction t " +
           "WHERE t.user.id = :userId AND t.transactionType = :type " +
           "AND t.transactionDate >= :fromDate AND t.transactionDate <= :toDate")
    long countByUserIdAndTypeAndDateBetween(
            @Param("userId") UUID userId,
            @Param("type") TransactionType type,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    List<Transaction> findByUser_IdAndTransactionTypeAndTransactionDateBetweenOrderByAmountDesc(
            UUID userId, TransactionType transactionType, LocalDate fromDate, LocalDate toDate);

    @Query("SELECT t FROM Transaction t WHERE t.sourceDocument.id = :sourceDocumentId AND t.user.id = :userId")
    List<Transaction> findBySourceDocumentIdAndUserId(
            @Param("sourceDocumentId") UUID sourceDocumentId,
            @Param("userId") UUID userId);

    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId " +
           "AND t.transactionType = :type " +
           "AND t.transferDirection = :direction " +
           "AND t.transferMatchStatus = :status " +
           "AND t.amount = :amount " +
           "ORDER BY t.transactionDate ASC")
    List<Transaction> findUnmatchedTransfers(
            @Param("userId") UUID userId,
            @Param("type") TransactionType type,
            @Param("direction") TransferDirection direction,
            @Param("status") TransferMatchStatus status,
            @Param("amount") BigDecimal amount);

    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId " +
           "AND (:accountId IS NULL OR t.account.id = :accountId) " +
           "AND t.transactionDate >= :fromDate AND t.transactionDate <= :toDate " +
           "ORDER BY t.transactionDate DESC, t.createdAt DESC")
    List<Transaction> findByUserIdAndAccountIdAndDateBetween(
            @Param("userId") UUID userId,
            @Param("accountId") UUID accountId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.user.id = :userId AND t.transactionType = :type AND t.transferDirection = :direction " +
           "AND t.transactionDate >= :fromDate AND t.transactionDate <= :toDate")
    BigDecimal sumAmountByUserIdAndTypeAndDirectionAndDateBetween(
            @Param("userId") UUID userId,
            @Param("type") TransactionType type,
            @Param("direction") TransferDirection direction,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query("SELECT t FROM Transaction t WHERE t.transactionDate >= :fromDate AND t.transactionDate <= :toDate ORDER BY t.transactionDate ASC, t.createdAt ASC")
    List<Transaction> findByTransactionDateBetween(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query("SELECT t FROM Transaction t WHERE t.transactionDate >= :fromDate AND t.transactionDate <= :toDate ORDER BY t.transactionDate ASC, t.createdAt ASC")
    List<Transaction> findByTransactionDateBetweenOrderByTransactionDateAsc(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query("SELECT COUNT(t.id) FROM Transaction t " +
           "WHERE t.user.id = :userId " +
           "AND t.transactionDate >= :fromDate AND t.transactionDate <= :toDate")
    long countTotalByUserIdAndDateBetween(
            @Param("userId") UUID userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query("SELECT COUNT(t.id) FROM Transaction t " +
           "WHERE t.user.id = :userId AND t.requiresReview = true " +
           "AND t.transactionDate >= :fromDate AND t.transactionDate <= :toDate")
    long countRequiresReviewByUserIdAndDateBetween(
            @Param("userId") UUID userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query("SELECT t.category, COUNT(t.id), COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.user.id = :userId AND t.transactionType = :type AND t.requiresReview = true " +
           "AND t.transactionDate >= :fromDate AND t.transactionDate <= :toDate " +
           "GROUP BY t.category")
    List<Object[]> findRequiresReviewCategorySpending(
            @Param("userId") UUID userId,
            @Param("type") TransactionType type,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
