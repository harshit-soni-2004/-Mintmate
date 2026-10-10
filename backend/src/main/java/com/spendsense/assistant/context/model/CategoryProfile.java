package com.spendsense.assistant.context.model;

import com.spendsense.analytics.TrendDirection;
import com.spendsense.transaction.TransactionCategory;

import java.math.BigDecimal;

public record CategoryProfile(
        TransactionCategory category,
        String displayName,
        BigDecimal averageMonthlySpend,
        BigDecimal recentMonthlySpend,
        BigDecimal minMonthlySpend,
        BigDecimal maxMonthlySpend,
        BigDecimal percentageOfExpenses,
        TrendDirection trend,
        String volatility, // LOW, MEDIUM, HIGH
        int monthsActive,
        DataSufficiency sufficiency,
        String behavioralSummary
) {}
