package com.spendsense.onboarding;

import com.spendsense.transaction.IncomeTier;
import java.math.BigDecimal;
import java.util.UUID;

public record IncomeClassificationResult(
        IncomeTier tier,
        boolean isIncome,
        boolean isVerified,
        boolean requiresReview,
        String reviewReason,
        UUID matchedSourceId,
        BigDecimal divergencePercentage
) {
    public static IncomeClassificationResult tier1Salary(UUID sourceId, boolean verified, boolean requiresReview, String reason, BigDecimal divergence) {
        return new IncomeClassificationResult(IncomeTier.TIER_1_SALARY, true, verified, requiresReview, reason, sourceId, divergence);
    }

    public static IncomeClassificationResult tier2Variable(UUID sourceId) {
        return new IncomeClassificationResult(IncomeTier.TIER_2_VARIABLE, true, false, false, null, sourceId, null);
    }

    public static IncomeClassificationResult tier3Refund() {
        return new IncomeClassificationResult(IncomeTier.TIER_3_REFUND, false, true, false, null, null, null);
    }

    public static IncomeClassificationResult tier4OtherCredit(String reason) {
        return new IncomeClassificationResult(IncomeTier.TIER_4_OTHER_CREDIT, false, false, false, reason, null, null);
    }
}
