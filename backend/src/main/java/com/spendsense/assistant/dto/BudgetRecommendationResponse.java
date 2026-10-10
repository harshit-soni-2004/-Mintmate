package com.spendsense.assistant.dto;

import com.spendsense.assistant.context.model.DataSufficiency;
import com.spendsense.transaction.TransactionCategory;

import java.math.BigDecimal;
import java.util.List;

public record BudgetRecommendationResponse(
        TransactionCategory category,
        BigDecimal recommendedBudget,
        BigDecimal recommendedRangeMin,
        BigDecimal recommendedRangeMax,
        String confidence,
        DataSufficiency dataSufficiency,
        int historyMonths,
        BigDecimal historicalAverage,
        BigDecimal recentAverage,
        BigDecimal existingBudgetLimit,
        List<String> supportingFacts,
        List<String> assumptions
) {}
