package com.spendsense.categorization;

import com.spendsense.transaction.TransactionCategory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record UserMemoryPreference(
        TransactionCategory category,
        String normalizedMerchant,
        double strengthScore,
        ConfidenceLevel confidenceLevel,
        int totalUsageCount,
        int categoryUsageCount,
        boolean hasConflict,
        boolean isStrongPreference,
        Instant lastUsedAt
) {
    public static final double HIGH_CONFIDENCE_THRESHOLD = 0.85;
    public static final double MEDIUM_CONFIDENCE_THRESHOLD = 0.60;
    public static final int MIN_OBSERVATIONS_FOR_STRONG = 2;
    public static final double MIN_RATIO_FOR_STRONG = 0.75;

    public static Optional<UserMemoryPreference> evaluate(String normalizedMerchant, List<UserCategorizationMemory> memories) {
        if (memories == null || memories.isEmpty() || normalizedMerchant == null || normalizedMerchant.isBlank()) {
            return Optional.empty();
        }

        int totalUsage = 0;
        UserCategorizationMemory topMemory = null;
        for (UserCategorizationMemory memory : memories) {
            totalUsage += memory.getUsageCount();
            if (topMemory == null || memory.getUsageCount() > topMemory.getUsageCount()) {
                topMemory = memory;
            } else if (memory.getUsageCount() == topMemory.getUsageCount()) {
                // In case of equal counts, break tie using recency
                if (memory.getLastUsedAt().isAfter(topMemory.getLastUsedAt())) {
                    topMemory = memory;
                }
            }
        }

        if (topMemory == null || totalUsage == 0) {
            return Optional.empty();
        }

        int topCount = topMemory.getUsageCount();
        double ratio = (double) topCount / totalUsage;
        boolean conflict = memories.size() > 1;

        // Sensible strength score:
        // A single correction is tentative (0.60, not an unquestionable permanent rule).
        // Consistent repetition scales to high confidence.
        // Conflicting history proportionally penalizes confidence.
        double strengthScore;
        if (totalUsage == 1) {
            strengthScore = 0.60;
        } else {
            double sampleFactor;
            if (totalUsage == 2) {
                sampleFactor = 0.85;
            } else if (totalUsage == 3) {
                sampleFactor = 0.92;
            } else {
                sampleFactor = 0.98;
            }
            strengthScore = Math.min(0.99, ratio * sampleFactor);
        }

        strengthScore = Math.round(strengthScore * 10000.0) / 10000.0;

        ConfidenceLevel level;
        if (strengthScore >= HIGH_CONFIDENCE_THRESHOLD) {
            level = ConfidenceLevel.HIGH;
        } else if (strengthScore >= MEDIUM_CONFIDENCE_THRESHOLD) {
            level = ConfidenceLevel.MEDIUM;
        } else {
            level = ConfidenceLevel.LOW;
        }

        boolean isStrong = totalUsage >= MIN_OBSERVATIONS_FOR_STRONG
                && ratio >= MIN_RATIO_FOR_STRONG
                && strengthScore >= HIGH_CONFIDENCE_THRESHOLD;

        return Optional.of(new UserMemoryPreference(
                topMemory.getCategory(),
                normalizedMerchant,
                strengthScore,
                level,
                totalUsage,
                topCount,
                conflict,
                isStrong,
                topMemory.getLastUsedAt()
        ));
    }
}
