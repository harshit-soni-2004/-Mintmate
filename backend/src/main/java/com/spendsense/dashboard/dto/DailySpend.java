package com.spendsense.dashboard.dto;

import com.spendsense.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySpend(LocalDate date, TransactionType type, BigDecimal totalAmount, long count) {
}
