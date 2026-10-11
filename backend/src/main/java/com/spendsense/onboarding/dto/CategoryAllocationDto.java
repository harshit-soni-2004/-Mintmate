package com.spendsense.onboarding.dto;

import com.spendsense.transaction.TransactionCategory;
import java.math.BigDecimal;

public record CategoryAllocationDto(
        TransactionCategory category,
        BigDecimal estimatedAmount
) {}
