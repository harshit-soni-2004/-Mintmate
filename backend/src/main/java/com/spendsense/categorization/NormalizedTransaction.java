package com.spendsense.categorization;

import java.util.Collections;
import java.util.List;

/**
 * Domain representation of normalized transaction text.
 * Provides a canonical merchant identifier for user memory and rule matching,
 * alongside clean composite text for future ML feature extraction.
 */
public record NormalizedTransaction(
        String rawMerchant,
        String rawDescription,
        String normalizedMerchant,
        String normalizedDescription,
        String compositeText,
        List<String> tokens
) {
    public NormalizedTransaction {
        tokens = tokens != null ? Collections.unmodifiableList(tokens) : Collections.emptyList();
    }
}
