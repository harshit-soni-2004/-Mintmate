package com.spendsense.analytics.dto;

import com.spendsense.analytics.TrendDirection;
import com.spendsense.transaction.TransactionCategory;
import java.math.BigDecimal;

public record CategoryAnalysis(
        TransactionCategory category,
        BigDecimal amount,
        BigDecimal percentage,
        BigDecimal previousAmount,
        BigDecimal changeAmount,
        BigDecimal percentageChange,
        TrendDirection direction,
        long transactionCount,
        long requiresReviewCount,
        BigDecimal requiresReviewAmount
) {
    public CategoryAnalysis(
            TransactionCategory category,
            BigDecimal amount,
            BigDecimal percentage,
            BigDecimal previousAmount,
            BigDecimal changeAmount,
            BigDecimal percentageChange,
            TrendDirection direction,
            long transactionCount
    ) {
        this(category, amount, percentage, previousAmount, changeAmount, percentageChange, direction, transactionCount, 0L, BigDecimal.ZERO);
    }
}
