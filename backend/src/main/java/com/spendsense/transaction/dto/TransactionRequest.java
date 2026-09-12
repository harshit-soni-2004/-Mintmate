package com.spendsense.transaction.dto;

import com.spendsense.transaction.PaymentMethod;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotBlank @Size(max = 120) String merchant,
        @Size(max = 255) String description,
        @NotNull TransactionCategory category,
        @NotNull TransactionType transactionType,
        PaymentMethod paymentMethod,
        @NotNull LocalDate transactionDate,
        @Size(max = 1000) String notes
) {
}
