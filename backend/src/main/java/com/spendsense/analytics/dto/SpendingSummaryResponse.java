package com.spendsense.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SpendingSummaryResponse(
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal netSavings,
        BigDecimal savingsRate,
        long transactionCount,
        BigDecimal averageExpense,
        BigDecimal largestExpense,
        LocalDate fromDate,
        LocalDate toDate
) {}
