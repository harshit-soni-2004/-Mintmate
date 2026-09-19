package com.spendsense.transaction.dto;

import com.spendsense.categorization.model.CategorizationCandidate;
import com.spendsense.transaction.TransactionCategory;

public record CategorizationCandidateDto(
        TransactionCategory category,
        double confidence
) {
    public static CategorizationCandidateDto from(CategorizationCandidate candidate) {
        if (candidate == null) {
            return null;
        }
        double rounded = Math.round(candidate.confidence() * 10000.0) / 10000.0;
        return new CategorizationCandidateDto(candidate.category(), rounded);
    }
}
