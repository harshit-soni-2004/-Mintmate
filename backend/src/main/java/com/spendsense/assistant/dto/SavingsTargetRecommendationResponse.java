package com.spendsense.assistant.dto;

import com.spendsense.assistant.context.model.DataSufficiency;

import java.math.BigDecimal;
import java.util.List;

public record SavingsTargetRecommendationResponse(
        BigDecimal recommendedMonthlySavings,
        BigDecimal recommendedRangeMin,
        BigDecimal recommendedRangeMax,
        BigDecimal recommendedSavingsRate,
        String confidence,
        DataSufficiency dataSufficiency,
        int historyMonths,
        BigDecimal averageMonthlyIncome,
        BigDecimal averageMonthlyExpenses,
        BigDecimal committedExpenses,
        BigDecimal requiredGoalSavings,
        List<String> supportingFacts,
        List<String> assumptions
) {}
