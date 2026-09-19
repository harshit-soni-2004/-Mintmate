package com.spendsense.categorization.model;

import com.spendsense.categorization.NormalizedTransaction;

import java.math.BigDecimal;
import java.time.DayOfWeek;

/**
 * Encapsulates transaction data provided for ML categorization inference.
 * Keeps feature extraction clean and separated from persistence and domain models.
 */
public record CategorizationInput(
        String normalizedMerchant,
        String compositeText,
        BigDecimal amount,
        String paymentMethod,
        DayOfWeek dayOfWeek
) {
    public static CategorizationInput from(NormalizedTransaction normalized) {
        if (normalized == null) {
            return new CategorizationInput(null, null, null, null, null);
        }
        return new CategorizationInput(
                normalized.normalizedMerchant(),
                normalized.compositeText(),
                null,
                null,
                null
        );
    }

    public static CategorizationInput from(
            NormalizedTransaction normalized,
            BigDecimal amount,
            String paymentMethod,
            DayOfWeek dayOfWeek
    ) {
        if (normalized == null) {
            return new CategorizationInput(null, null, amount, paymentMethod, dayOfWeek);
        }
        return new CategorizationInput(
                normalized.normalizedMerchant(),
                normalized.compositeText(),
                amount,
                paymentMethod,
                dayOfWeek
        );
    }

    public static CategorizationInput of(String compositeText) {
        return new CategorizationInput(null, compositeText, null, null, null);
    }

    public static CategorizationInput of(String normalizedMerchant, String compositeText) {
        return new CategorizationInput(normalizedMerchant, compositeText, null, null, null);
    }
}
