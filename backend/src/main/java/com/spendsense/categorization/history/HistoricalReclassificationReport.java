package com.spendsense.categorization.history;

import com.spendsense.transaction.TransactionCategory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Summary report returned from a dry-run historical reclassification run.
 */
public record HistoricalReclassificationReport(
        LocalDate startDate,
        LocalDate endDate,
        int totalInspected,
        int totalChanged,
        int totalPreservedManual,
        int totalRequiringReview,
        Map<TransactionCategory, Long> currentDistribution,
        Map<TransactionCategory, Long> proposedDistribution,
        Map<String, Long> transitions,
        Map<String, Long> ruleMatches,
        List<HistoricalReclassificationItem> sampleChangedItems
) {}
