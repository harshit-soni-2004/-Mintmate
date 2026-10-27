package com.spendsense.categorization.history;

import com.spendsense.categorization.CategorizationSource;
import com.spendsense.categorization.ConfidenceLevel;
import com.spendsense.transaction.TransactionCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HistoricalReclassificationSnapshot(
        String backupId,
        Instant createdAt,
        LocalDate startDate,
        LocalDate endDate,
        List<TransactionState> states
) {
    public record TransactionState(
            UUID transactionId,
            String merchant,
            TransactionCategory category,
            TransactionCategory predictedCategory,
            boolean requiresReview,
            BigDecimal categorizationConfidence,
            ConfidenceLevel categorizationConfidenceLevel,
            CategorizationSource categorizationSource
    ) {}
}
