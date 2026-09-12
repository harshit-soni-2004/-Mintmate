package com.spendsense.analytics.dto;

import com.spendsense.transaction.TransactionCategory;
import java.math.BigDecimal;
import java.util.List;

public record CategoryBreakdownResponse(
        BigDecimal totalExpenses,
        List<CategoryAnalysis> categories,
        TransactionCategory topCategory
) {}
