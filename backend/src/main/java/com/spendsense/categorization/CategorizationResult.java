package com.spendsense.categorization;

import com.spendsense.categorization.model.CategorizationCandidate;
import com.spendsense.transaction.TransactionCategory;

import java.util.Collections;
import java.util.List;

/**
 * Domain-level result of transaction categorization orchestration.
 * Exposes category, confidence, source, and decision flags without leaking ML internals.
 */
public record CategorizationResult(
        TransactionCategory category,
        double confidence,
        ConfidenceLevel confidenceLevel,
        CategorizationSource source,
        List<CategorizationCandidate> alternatives,
        String modelVersion,
        boolean personalizationApplied,
        boolean requiresUserConfirmation,
        String rationale,
        String normalizedMerchant
) {
    public CategorizationResult {
        alternatives = alternatives != null ? Collections.unmodifiableList(alternatives) : Collections.emptyList();
    }

    public static CategorizationResult fromRule(
            TransactionCategory category,
            double confidence,
            ConfidenceLevel confidenceLevel,
            String ruleId,
            String explanation,
            String normalizedMerchant
    ) {
        return new CategorizationResult(
                category,
                confidence,
                confidenceLevel,
                CategorizationSource.RULE,
                List.of(new CategorizationCandidate(category, confidence)),
                ruleId,
                false,
                false,
                explanation,
                normalizedMerchant
        );
    }

    public static CategorizationResult fromUserMemory(
            TransactionCategory category,
            double confidence,
            ConfidenceLevel confidenceLevel,
            boolean isStrong,
            String explanation,
            String normalizedMerchant,
            List<CategorizationCandidate> alternatives
    ) {
        return new CategorizationResult(
                category,
                confidence,
                confidenceLevel,
                CategorizationSource.USER_MEMORY,
                alternatives,
                null,
                true,
                !isStrong,
                explanation,
                normalizedMerchant
        );
    }

    public static CategorizationResult fromMl(
            TransactionCategory category,
            double confidence,
            ConfidenceLevel confidenceLevel,
            List<CategorizationCandidate> alternatives,
            String modelVersion,
            boolean personalizationApplied,
            boolean requiresUserConfirmation,
            String rationale,
            String normalizedMerchant
    ) {
        return new CategorizationResult(
                category,
                confidence,
                confidenceLevel,
                CategorizationSource.ML,
                alternatives,
                modelVersion,
                personalizationApplied,
                requiresUserConfirmation,
                rationale,
                normalizedMerchant
        );
    }

    public static CategorizationResult fromFallback(
            TransactionCategory category,
            String rationale,
            String normalizedMerchant
    ) {
        return new CategorizationResult(
                category,
                0.0,
                ConfidenceLevel.NONE,
                CategorizationSource.FALLBACK,
                List.of(new CategorizationCandidate(category, 0.0)),
                null,
                false,
                true,
                rationale,
                normalizedMerchant
        );
    }
}
