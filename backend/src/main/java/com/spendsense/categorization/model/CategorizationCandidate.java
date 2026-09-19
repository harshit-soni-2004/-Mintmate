package com.spendsense.categorization.model;

import com.spendsense.transaction.TransactionCategory;

/**
 * Represents a single category candidate prediction along with its model confidence probability.
 */
public record CategorizationCandidate(
        TransactionCategory category,
        double confidence
) implements Comparable<CategorizationCandidate> {

    @Override
    public int compareTo(CategorizationCandidate other) {
        // Sort descending by confidence
        return Double.compare(other.confidence(), this.confidence());
    }
}
