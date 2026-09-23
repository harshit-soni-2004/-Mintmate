package com.spendsense.account.dto;

import com.spendsense.document.DocumentAccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FinancialAccountRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String institutionName,
        @NotNull DocumentAccountType accountType,
        @Pattern(regexp = "^[0-9A-Za-z]{4}$", message = "Last 4 must be exactly 4 alphanumeric characters") String accountNumberLast4,
        String currency,
        Boolean primary
) {
}
