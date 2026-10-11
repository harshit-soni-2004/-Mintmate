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
        BigDecimal investmentContributions,
        BigDecimal declaredIncome,
        BigDecimal monthlySavingsTarget,
        BigDecimal monthlyInvestmentTarget,
        boolean hasOnboardingBaseline,
        BigDecimal debtOwedToUser,
        BigDecimal debtOwedByUser,
        BigDecimal netDebtPosition,
        int activeDebtRemindersCount
) {
    public DashboardSummaryResponse(
            BigDecimal totalIncome,
            BigDecimal totalExpenses,
            BigDecimal netSavings,
            BigDecimal savingsRate,
            BigDecimal previousPeriodIncome,
            BigDecimal previousPeriodExpenses,
            BigDecimal incomeChangePercentage,
            BigDecimal expenseChangePercentage,
            BigDecimal totalTransfers,
            BigDecimal investmentContributions,
            BigDecimal declaredIncome,
            BigDecimal monthlySavingsTarget,
            BigDecimal monthlyInvestmentTarget,
            boolean hasOnboardingBaseline
    ) {
        this(totalIncome, totalExpenses, netSavings, savingsRate, previousPeriodIncome, previousPeriodExpenses,
                incomeChangePercentage, expenseChangePercentage, totalTransfers, investmentContributions,
                declaredIncome, monthlySavingsTarget, monthlyInvestmentTarget, hasOnboardingBaseline,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
    }

    public DashboardSummaryResponse(
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
        this(totalIncome, totalExpenses, netSavings, savingsRate, previousPeriodIncome, previousPeriodExpenses,
                incomeChangePercentage, expenseChangePercentage, totalTransfers, investmentContributions,
                null, null, null, false,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
    }

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
        this(totalIncome, totalExpenses, netSavings, savingsRate, previousPeriodIncome, previousPeriodExpenses,
                incomeChangePercentage, expenseChangePercentage, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, false,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
    }
}
