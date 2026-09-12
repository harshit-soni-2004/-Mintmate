package com.spendsense.analytics.dto;

import com.spendsense.analytics.TrendDirection;
import java.math.BigDecimal;

public record TrendMetric(
        BigDecimal currentAmount,
        BigDecimal previousAmount,
        BigDecimal changeAmount,
        BigDecimal percentageChange,
        TrendDirection direction
) {}
