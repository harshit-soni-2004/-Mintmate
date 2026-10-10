package com.spendsense.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PeriodComparisonResponse(
        LocalDate currentFrom,
        LocalDate currentTo,
        LocalDate previousFrom,
        LocalDate previousTo,
        TrendMetric income,
        TrendMetric expenses,
        TrendMetric netSavings,
        BigDecimal currentSavingsRate,
        BigDecimal previousSavingsRate,
        BigDecimal savingsRateChange,
        List<CategoryComparison> categoryChanges,
        List<CategoryComparison> largestIncreases,
        List<CategoryComparison> largestDecreases
) {
    public PeriodComparisonResponse(
            LocalDate currentFrom,
            LocalDate currentTo,
            LocalDate previousFrom,
            LocalDate previousTo,
            TrendMetric income,
            TrendMetric expenses,
            TrendMetric netSavings,
            BigDecimal currentSavingsRate,
            BigDecimal previousSavingsRate,
            BigDecimal savingsRateChange
    ) {
        this(currentFrom, currentTo, previousFrom, previousTo, income, expenses, netSavings,
                currentSavingsRate, previousSavingsRate, savingsRateChange, List.of(), List.of(), List.of());
    }

    public BigDecimal currentExpenses() {
        return expenses != null ? expenses.currentAmount() : BigDecimal.ZERO;
    }

    public BigDecimal previousExpenses() {
        return expenses != null ? expenses.previousAmount() : BigDecimal.ZERO;
    }

    public BigDecimal absoluteChange() {
        return expenses != null ? expenses.changeAmount() : BigDecimal.ZERO;
    }

    public BigDecimal percentageChange() {
        return expenses != null ? expenses.percentageChange() : BigDecimal.ZERO;
    }

    public BigDecimal currentIncome() {
        return income != null ? income.currentAmount() : BigDecimal.ZERO;
    }

    public BigDecimal previousIncome() {
        return income != null ? income.previousAmount() : BigDecimal.ZERO;
    }

    public BigDecimal incomeChange() {
        return income != null ? income.changeAmount() : BigDecimal.ZERO;
    }

    public BigDecimal currentSavings() {
        return netSavings != null ? netSavings.currentAmount() : BigDecimal.ZERO;
    }

    public BigDecimal previousSavings() {
        return netSavings != null ? netSavings.previousAmount() : BigDecimal.ZERO;
    }

    public BigDecimal savingsChange() {
        return netSavings != null ? netSavings.changeAmount() : BigDecimal.ZERO;
    }
}
