package com.spendsense.categorization.history;

import com.spendsense.categorization.CategorizationSource;
import com.spendsense.categorization.ConfidenceLevel;
import com.spendsense.transaction.TransactionCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Record representing a single transaction's evaluation in the dry-run reclassification.
 */
public record HistoricalReclassificationItem(
        UUID transactionId,
        LocalDate transactionDate,
        BigDecimal amount,
        String originalMerchant,
        String originalDescription,
        String proposedDisplayMerchant,
        TransactionCategory currentCategory,
        TransactionCategory proposedCategory,
        TransactionCategory predictedCategory,
        double confidence,
        ConfidenceLevel confidenceLevel,
        CategorizationSource source,
        boolean requiresReview,
        String evidence,
        boolean manualPreserved,
        boolean changed
) {}
