package com.spendsense.budget.dto;

import com.spendsense.budget.Budget;
import com.spendsense.budget.BudgetPeriod;
import com.spendsense.budget.BudgetStatus;
import com.spendsense.transaction.TransactionCategory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BudgetResponse(
        UUID id,
        TransactionCategory category,
        BigDecimal limitAmount,
        BudgetPeriod period,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal spentAmount,
        BigDecimal remainingAmount,
        double percentageUsed,
        long transactionCount,
        BudgetStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static BudgetResponse of(Budget budget, BigDecimal spentAmount, BigDecimal remainingAmount,
                                    double percentageUsed, long transactionCount, BudgetStatus status) {
        return new BudgetResponse(
                budget.getId(),
                budget.getCategory(),
                budget.getLimitAmount(),
                budget.getPeriod(),
                budget.getStartDate(),
                budget.getEndDate(),
                spentAmount,
                remainingAmount,
                percentageUsed,
                transactionCount,
                status,
                budget.getCreatedAt(),
                budget.getUpdatedAt()
        );
    }
}
