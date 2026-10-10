package com.spendsense.assistant.dto;

import com.spendsense.assistant.context.model.DataSufficiency;

import java.math.BigDecimal;
import java.util.List;

public record SpendingFlexibilityResponse(
        BigDecimal monthlyIncome,
        BigDecimal committedMonthlyExpenses,
        BigDecimal typicalBaselineExpenses,
        BigDecimal availableDiscretionaryCapacity,
        BigDecimal committedPercentage,
        String confidence,
        DataSufficiency dataSufficiency,
        String methodology,
        List<String> breakdownItems
) {}
