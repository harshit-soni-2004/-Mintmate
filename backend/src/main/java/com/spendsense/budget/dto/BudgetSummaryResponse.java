package com.spendsense.budget.dto;

import java.math.BigDecimal;
import java.util.List;

public record BudgetSummaryResponse(
        BigDecimal totalBudget,
        BigDecimal totalSpent,
        BigDecimal totalRemaining,
        double percentageUsed,
        int onTrackCount,
        int warningCount,
        int exceededCount,
        int totalBudgets,
        List<BudgetResponse> budgets
) {
}
