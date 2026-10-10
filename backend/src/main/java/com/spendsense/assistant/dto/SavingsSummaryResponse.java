package com.spendsense.assistant.dto;

import com.spendsense.analytics.TrendDirection;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SavingsSummaryResponse(
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal netSavings,
        BigDecimal savingsRate,
        String savingsStatus,
        BigDecimal previousNetSavings,
        BigDecimal previousSavingsRate,
        BigDecimal savingsRateChange,
        TrendDirection direction,
        LocalDate fromDate,
        LocalDate toDate
) {}
