package com.spendsense.analytics.dto;

import com.spendsense.analytics.TrendDirection;
import com.spendsense.transaction.TransactionCategory;
import java.math.BigDecimal;

public record CategoryAnalysis(
        TransactionCategory category,
        BigDecimal amount,
        BigDecimal percentage,
        BigDecimal previousAmount,
        BigDecimal changeAmount,
        BigDecimal percentageChange,
        TrendDirection direction,
        long transactionCount
) {}
