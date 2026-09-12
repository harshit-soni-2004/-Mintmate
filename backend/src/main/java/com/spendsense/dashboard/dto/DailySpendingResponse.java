package com.spendsense.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySpendingResponse(
        LocalDate date,
        BigDecimal totalExpense,
        BigDecimal totalIncome,
        long transactionCount
) {
}
