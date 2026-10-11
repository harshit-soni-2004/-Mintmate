package com.spendsense.budget;

import com.spendsense.transaction.TransactionCategory;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findByUser_IdOrderByCategoryAsc(UUID userId);

    @Query("SELECT b FROM Budget b WHERE b.user.id = :userId " +
           "AND b.startDate <= :toDate AND b.endDate >= :fromDate " +
           "ORDER BY b.category ASC")
    List<Budget> findActiveBudgets(
            @Param("userId") UUID userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    Optional<Budget> findByIdAndUser_Id(UUID id, UUID userId);

    boolean existsByUser_IdAndCategoryAndPeriodAndStartDate(
            UUID userId, TransactionCategory category, BudgetPeriod period, LocalDate startDate);

    boolean existsByUser_IdAndCategoryAndPeriodAndStartDateAndIdNot(
            UUID userId, TransactionCategory category, BudgetPeriod period, LocalDate startDate, UUID id);

    boolean existsByUser_IdAndCategory(UUID userId, TransactionCategory category);
}
