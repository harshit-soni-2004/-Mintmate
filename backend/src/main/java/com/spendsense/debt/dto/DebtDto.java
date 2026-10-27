package com.spendsense.debt.dto;

import com.spendsense.debt.Debt;
import com.spendsense.debt.DebtStatus;
import com.spendsense.debt.DebtType;
import com.spendsense.transaction.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DebtDto(
        UUID id,
        String counterpartyName,
        DebtType debtType,
        BigDecimal amount,
        LocalDate dueDate,
        DebtStatus status,
        Instant settledAt,
        UUID linkedTransactionId,
        BigDecimal linkedTransactionAmount,
        LocalDate linkedTransactionDate,
        String notes,
        Instant createdAt,
        boolean overdue
) {
    public static DebtDto fromEntity(Debt debt) {
        boolean isOverdue = debt.getStatus() == DebtStatus.ACTIVE
                && debt.getDueDate() != null
                && debt.getDueDate().isBefore(LocalDate.now());

        Transaction linked = debt.getLinkedTransaction();
        UUID linkedTxId = linked != null ? linked.getId() : null;
        BigDecimal linkedTxAmount = linked != null ? linked.getAmount() : null;
        LocalDate linkedTxDate = linked != null ? linked.getTransactionDate() : null;

        return new DebtDto(
                debt.getId(),
                debt.getCounterpartyName(),
                debt.getDebtType(),
                debt.getAmount(),
                debt.getDueDate(),
                debt.getStatus(),
                debt.getSettledAt(),
                linkedTxId,
                linkedTxAmount,
                linkedTxDate,
                debt.getNotes(),
                debt.getCreatedAt(),
                isOverdue
        );
    }
}
