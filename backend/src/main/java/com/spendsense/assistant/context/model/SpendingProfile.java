package com.spendsense.assistant.context.model;

import com.spendsense.analytics.TrendDirection;

import java.math.BigDecimal;
import java.util.List;

public record SpendingProfile(
        BigDecimal averageMonthlyExpenses,
        BigDecimal medianMonthlyExpenses,
        BigDecimal recentMonthlyExpenses,
        TrendDirection trend,
        String volatility, // LOW, MEDIUM, HIGH
        BigDecimal averageMonthlySavings,
        List<String> topSpendingCategories,
        BigDecimal estimatedMonthlyRecurring,
        BigDecimal estimatedDiscretionaryFlexibility,
        DataSufficiency sufficiency
) {}
