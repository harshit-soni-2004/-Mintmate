package com.spendsense.analytics.dto;

import com.spendsense.analytics.AnomalyType;
import com.spendsense.transaction.TransactionCategory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SpendingAnomaly(
        String id,
        AnomalyType type,
        String title,
        String description,
        String reason,
        BigDecimal amount,
        BigDecimal baselineAmount,
        BigDecimal percentageOrMultiplier,
        TransactionCategory category,
        LocalDate date,
        UUID transactionId
) {}
