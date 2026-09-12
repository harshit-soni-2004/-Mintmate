package com.spendsense.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SpendingTrendPoint(
        String label,
        LocalDate date,
        BigDecimal totalExpense,
        BigDecimal totalIncome,
        long transactionCount
) {}
