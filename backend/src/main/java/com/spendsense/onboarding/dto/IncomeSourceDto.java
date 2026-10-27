package com.spendsense.onboarding.dto;

import com.spendsense.onboarding.IncomeFrequency;
import com.spendsense.onboarding.IncomeType;
import java.math.BigDecimal;

public record IncomeSourceDto(
        String sourceName,
        IncomeType incomeType,
        IncomeFrequency frequency,
        BigDecimal declaredAmount,
        Integer expectedDayOfMonth,
        String employerOrPayerKeyword,
        boolean primary,
        boolean variable
) {
    public IncomeSourceDto(String sourceName, IncomeType incomeType, IncomeFrequency frequency, BigDecimal declaredAmount, Integer expectedDayOfMonth, String employerOrPayerKeyword) {
        this(sourceName, incomeType, frequency, declaredAmount, expectedDayOfMonth, employerOrPayerKeyword, true, false);
    }
}
