package com.spendsense.categorization;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload for categorization orchestration.
 */
public record CategorizationRequest(
        UUID userId,
        String merchant,
        String description,
        BigDecimal amount,
        String paymentMethod,
        LocalDate transactionDate
) {
    public static CategorizationRequest of(String merchant) {
        return new CategorizationRequest(null, merchant, null, null, null, null);
    }

    public static CategorizationRequest of(String merchant, String description) {
        return new CategorizationRequest(null, merchant, description, null, null, null);
    }

    public static CategorizationRequest of(UUID userId, String merchant, String description) {
        return new CategorizationRequest(userId, merchant, description, null, null, null);
    }

    public static CategorizationRequest of(
            UUID userId,
            String merchant,
            String description,
            BigDecimal amount,
            String paymentMethod,
            LocalDate transactionDate
    ) {
        return new CategorizationRequest(userId, merchant, description, amount, paymentMethod, transactionDate);
    }
}
