package com.spendsense.assistant.dto;

import com.spendsense.analytics.TrendDirection;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IncomeSummaryResponse(
        BigDecimal totalIncome,
        long transactionCount,
        BigDecimal averageIncomeTransaction,
        BigDecimal previousPeriodIncome,
        BigDecimal incomeChange,
        BigDecimal incomePercentageChange,
        TrendDirection direction,
        LocalDate fromDate,
        LocalDate toDate
) {}
