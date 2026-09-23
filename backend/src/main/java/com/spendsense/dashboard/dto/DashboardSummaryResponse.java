package com.spendsense.dashboard.dto;

import java.math.BigDecimal;

public record DashboardSummaryResponse(
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal netSavings,
        BigDecimal savingsRate,
        BigDecimal previousPeriodIncome,
        BigDecimal previousPeriodExpenses,
        BigDecimal incomeChangePercentage,
        BigDecimal expenseChangePercentage,
        BigDecimal totalTransfers,
        BigDecimal investmentContributions
) {
    public DashboardSummaryResponse(
            BigDecimal totalIncome,
            BigDecimal totalExpenses,
            BigDecimal netSavings,
            BigDecimal savingsRate,
            BigDecimal previousPeriodIncome,
            BigDecimal previousPeriodExpenses,
            BigDecimal incomeChangePercentage,
            BigDecimal expenseChangePercentage
    ) {
        this(totalIncome, totalExpenses, netSavings, savingsRate, previousPeriodIncome, previousPeriodExpenses, incomeChangePercentage, expenseChangePercentage, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
