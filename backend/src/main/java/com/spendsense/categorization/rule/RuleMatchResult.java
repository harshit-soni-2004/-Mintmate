package com.spendsense.categorization.rule;

import com.spendsense.categorization.ConfidenceLevel;
import com.spendsense.transaction.TransactionCategory;

/**
 * Result returned when a deterministic categorization rule fires.
 */
public record RuleMatchResult(
        TransactionCategory category,
        double confidence,
        ConfidenceLevel confidenceLevel,
        String ruleId,
        String explanation
) {
    public static RuleMatchResult matched(String ruleId, TransactionCategory category, String explanation) {
        return new RuleMatchResult(category, 1.0, ConfidenceLevel.HIGH, ruleId, explanation);
    }
}
