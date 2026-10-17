package com.spendsense.debt.dto;

import com.spendsense.transaction.TransactionType;
import com.spendsense.transaction.TransferDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DebtCandidateTransactionDto(
        UUID id,
        BigDecimal amount,
        String description,
        String merchant,
        LocalDate transactionDate,
        TransactionType transactionType,
        TransferDirection transferDirection,
        boolean isTopMatch,
        String matchReason,
        int score
) {}
