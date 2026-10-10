package com.spendsense.analytics.dto;

import java.math.BigDecimal;

/**
 * Deterministic category-level comparison between two periods.
 */
public record CategoryComparison(
        String category,
        BigDecimal current,
        BigDecimal previous,
        BigDecimal change,
        BigDecimal changePercentage
) {}
