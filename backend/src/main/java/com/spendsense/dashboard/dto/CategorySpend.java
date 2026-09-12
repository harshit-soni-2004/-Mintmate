package com.spendsense.dashboard.dto;

import com.spendsense.transaction.TransactionCategory;
import java.math.BigDecimal;

public record CategorySpend(TransactionCategory category, BigDecimal totalAmount, long count) {
}
