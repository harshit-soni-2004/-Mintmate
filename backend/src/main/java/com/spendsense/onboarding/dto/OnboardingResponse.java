package com.spendsense.onboarding.dto;

import com.spendsense.onboarding.UserPersona;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OnboardingResponse(
        UUID profileId,
        UserPersona persona,
        boolean onboardingCompleted,
        BigDecimal monthlySavingsTarget,
        BigDecimal monthlyInvestmentTarget,
        List<IncomeSourceDto> incomeSources,
        List<CategoryAllocationDto> categoryAllocations
) {}
