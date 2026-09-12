package com.spendsense.budget.dto;

import com.spendsense.budget.BudgetPeriod;
import com.spendsense.transaction.TransactionCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record BudgetRequest(
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 17, fraction = 2) BigDecimal limitAmount,
        @NotNull TransactionCategory category,
        BudgetPeriod period,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {
}
