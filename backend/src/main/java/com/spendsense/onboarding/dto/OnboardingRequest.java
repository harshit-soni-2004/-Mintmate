package com.spendsense.onboarding.dto;

import com.spendsense.onboarding.UserPersona;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record OnboardingRequest(
        @NotNull UserPersona persona,
        BigDecimal monthlySavingsTarget,
        BigDecimal monthlyInvestmentTarget,
        List<IncomeSourceDto> incomeSources,
        List<CategoryAllocationDto> categoryAllocations
) {}
