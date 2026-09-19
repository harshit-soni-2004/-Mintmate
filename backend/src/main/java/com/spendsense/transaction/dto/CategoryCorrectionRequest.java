package com.spendsense.transaction.dto;

import com.spendsense.transaction.TransactionCategory;
import jakarta.validation.constraints.NotNull;

public record CategoryCorrectionRequest(
        @NotNull(message = "Category cannot be null")
        TransactionCategory category
) {
}
