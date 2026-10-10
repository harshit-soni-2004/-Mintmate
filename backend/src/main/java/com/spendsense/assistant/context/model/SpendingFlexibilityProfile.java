package com.spendsense.assistant.context.model;

import java.math.BigDecimal;

public record SpendingFlexibilityProfile(
        BigDecimal baselineMonthlyIncome,
        BigDecimal committedExpenses,
        BigDecimal typicalLivingExpenses,
        BigDecimal availableDiscretionaryCapacity,
        String methodologyNotes,
        DataSufficiency sufficiency
) {}
