package com.spendsense.goal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record GoalRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 17, fraction = 2) BigDecimal targetAmount,
        @DecimalMin(value = "0.00") @Digits(integer = 17, fraction = 2) BigDecimal currentAmount,
        LocalDate targetDate
) {
}
