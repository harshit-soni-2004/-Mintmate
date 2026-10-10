package com.spendsense.assistant.dto;

import com.spendsense.analytics.TrendDirection;
import com.spendsense.analytics.dto.CategoryComparison;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SpendingChangeAnalysisResponse(
        BigDecimal currentExpenses,
        BigDecimal previousExpenses,
        BigDecimal absoluteChange,
        BigDecimal percentageChange,
        TrendDirection direction,
        List<CategoryComparison> primaryDrivers,
        List<CategoryComparison> categoryChanges,
        LocalDate currentFrom,
        LocalDate currentTo,
        LocalDate previousFrom,
        LocalDate previousTo
) {}
