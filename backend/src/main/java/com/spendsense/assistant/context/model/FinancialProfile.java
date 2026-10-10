package com.spendsense.assistant.context.model;

import com.spendsense.budget.dto.BudgetResponse;
import com.spendsense.goal.dto.GoalResponse;

import java.time.LocalDate;
import java.util.List;

public record FinancialProfile(
        LocalDate profileDate,
        int historyMonths,
        DataSufficiencyProfile dataSufficiency,
        IncomeProfile income,
        SpendingProfile spending,
        SavingsProfile savings,
        List<CategoryProfile> categories,
        RecurringCommitmentProfile recurring,
        SpendingFlexibilityProfile flexibility,
        List<BudgetResponse> activeBudgets,
        List<GoalResponse> activeGoals
) {}
