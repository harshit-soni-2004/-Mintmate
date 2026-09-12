package com.spendsense.analytics.dto;

import com.spendsense.analytics.InsightSeverity;
import com.spendsense.analytics.InsightType;
import com.spendsense.transaction.TransactionCategory;
import java.math.BigDecimal;

public record FinancialInsight(
        String id,
        InsightType type,
        InsightSeverity severity,
        String title,
        String description,
        TransactionCategory category,
        BigDecimal amount,
        BigDecimal percentageChange,
        String recommendation
) {}
