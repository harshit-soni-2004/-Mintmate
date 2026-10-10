package com.spendsense.assistant.context.model;

import com.spendsense.analytics.TrendDirection;

import java.math.BigDecimal;

public record SavingsProfile(
        BigDecimal averageMonthlySavings,
        BigDecimal recentMonthlySavings,
        BigDecimal averageSavingsRate,
        BigDecimal recentSavingsRate,
        TrendDirection trend,
        BigDecimal totalGoalTarget,
        BigDecimal totalGoalProgress,
        BigDecimal totalGoalRemaining,
        BigDecimal requiredMonthlyGoalSavings,
        BigDecimal monthlyGoalSavingsGap,
        boolean onTrackForGoals,
        DataSufficiency sufficiency
) {}
