package com.spendsense.categorization.history;

import com.spendsense.transaction.TransactionCategory;

import java.time.LocalDate;
import java.util.Map;

public record HistoricalReclassificationApplyResult(
        LocalDate startDate,
        LocalDate endDate,
        int totalInspected,
        int totalUpdated,
        int totalPreservedManual,
        int totalRequiringReview,
        String backupFilePath,
        Map<TransactionCategory, Long> postApplyDistribution
) {}
