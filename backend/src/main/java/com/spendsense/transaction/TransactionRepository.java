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
           "AND t.transactionDate >= :fromDate AND t.transactionDate <= :toDate " +
           "GROUP BY t.transactionDate, t.transactionType ORDER BY t.transactionDate ASC")
    List<DailySpend> findDailySpending(
            @Param("userId") UUID userId,
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
}
