package com.spendsense.assistant.context.model;

import com.spendsense.analytics.TrendDirection;

import java.math.BigDecimal;

public record IncomeProfile(
        BigDecimal averageMonthlyIncome,
        BigDecimal medianMonthlyIncome,
        BigDecimal recentMonthlyIncome,
        TrendDirection trend,
        String stability, // HIGH, MEDIUM, LOW
        int historyMonths,
        DataSufficiency sufficiency
) {}
