package com.spendsense.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PeriodComparisonResponse(
        LocalDate currentFrom,
        LocalDate currentTo,
        LocalDate previousFrom,
        LocalDate previousTo,
        TrendMetric income,
        TrendMetric expenses,
        TrendMetric netSavings,
        BigDecimal currentSavingsRate,
        BigDecimal previousSavingsRate,
        BigDecimal savingsRateChange
) {}
