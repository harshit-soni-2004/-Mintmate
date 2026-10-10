package com.spendsense.assistant.dto;

import com.spendsense.transaction.TransactionCategory;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringExpenseItem(
        String merchant,
        TransactionCategory category,
        BigDecimal averageAmount,
        BigDecimal lastAmount,
        String frequency,
        String confidence,
        int occurrenceCount,
        LocalDate lastOccurrenceDate
) {}
