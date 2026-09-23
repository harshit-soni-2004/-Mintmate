package com.spendsense.account.dto;

import com.spendsense.account.FinancialAccount;
import com.spendsense.document.DocumentAccountType;

import java.time.Instant;
import java.util.UUID;

public record FinancialAccountResponse(
        UUID id,
        String name,
        String institutionName,
        DocumentAccountType accountType,
        String accountNumberLast4,
        String currency,
        boolean active,
        boolean primary,
        Instant createdAt,
        Instant updatedAt
) {
    public static FinancialAccountResponse from(FinancialAccount account) {
        if (account == null) return null;
        return new FinancialAccountResponse(
                account.getId(),
                account.getName(),
                account.getInstitutionName(),
                account.getAccountType(),
                account.getAccountNumberLast4(),
                account.getCurrency(),
                account.isActive(),
                account.isPrimary(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
