package com.spendsense.assistant.dto;

import java.math.BigDecimal;
import java.util.List;

public record RecurringExpensesResponse(
        BigDecimal totalMonthlyRecurring,
        List<RecurringExpenseItem> items,
        int analyzedPeriodDays
) {}
