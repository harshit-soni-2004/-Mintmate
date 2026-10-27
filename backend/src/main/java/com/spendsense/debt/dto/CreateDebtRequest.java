package com.spendsense.debt.dto;

import com.spendsense.debt.DebtType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateDebtRequest(
        @NotBlank(message = "Counterparty name is required")
        String counterpartyName,

        @NotNull(message = "Debt type is required")
        DebtType debtType,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        LocalDate dueDate,

        String notes
) {}
