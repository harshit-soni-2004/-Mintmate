package com.spendsense.analytics.dto;

import com.spendsense.transaction.TransactionCategory;
import java.math.BigDecimal;
import java.util.List;

public record CategoryBreakdownResponse(
        BigDecimal totalExpenses,
        List<CategoryAnalysis> categories,
        TransactionCategory topCategory,
        long totalTransactions,
        long autoClassifiedCount,
        long requiresReviewCount,
        BigDecimal autoClassifiedPercentage,
        BigDecimal requiresReviewPercentage
) {
    public CategoryBreakdownResponse(
            BigDecimal totalExpenses,
            List<CategoryAnalysis> categories,
            TransactionCategory topCategory
    ) {
        this(totalExpenses, categories, topCategory, 0L, 0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
