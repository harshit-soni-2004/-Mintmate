package com.spendsense.categorization.model;

import com.spendsense.transaction.TransactionCategory;

import java.util.Collections;
import java.util.List;

/**
 * Output of the categorization model inference.
 * Provides the top predicted category, raw calibrated probability confidence,
 * ranked alternatives distribution, and the model artifact version that produced it.
 */
public record CategorizationPrediction(
        TransactionCategory predictedCategory,
        double confidence,
        List<CategorizationCandidate> alternatives,
        String modelVersion
) {
    public CategorizationPrediction {
        alternatives = alternatives != null ? Collections.unmodifiableList(alternatives) : Collections.emptyList();
    }
}
