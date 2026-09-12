package com.spendsense.dashboard.dto;

import com.spendsense.transaction.TransactionCategory;
import java.math.BigDecimal;

public record CategorySpendingResponse(
        TransactionCategory category,
        BigDecimal amount,
        BigDecimal percentage,
        long transactionCount
) {
}
