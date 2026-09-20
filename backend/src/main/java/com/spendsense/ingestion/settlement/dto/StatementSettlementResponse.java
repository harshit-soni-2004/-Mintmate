package com.spendsense.ingestion.settlement.dto;

import com.spendsense.document.DocumentAccountType;
import com.spendsense.ingestion.SourceTransactionClassification;
import com.spendsense.ingestion.settlement.SettlementStatus;
import com.spendsense.ingestion.settlement.StatementSettlement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StatementSettlementResponse(
        UUID id,
        UUID documentId,
        int rowIndex,
        LocalDate transactionDate,
        BigDecimal amount,
        String rawMerchant,
        String rawDescription,
        SourceTransactionClassification classification,
        DocumentAccountType accountType,
        String externalReference,
        SettlementStatus status,
        UUID matchedSettlementId
) {
    public static StatementSettlementResponse from(StatementSettlement s) {
        return new StatementSettlementResponse(
                s.getId(),
                s.getDocument().getId(),
                s.getRowIndex(),
                s.getTransactionDate(),
                s.getAmount(),
                s.getRawMerchant(),
                s.getRawDescription(),
                s.getClassification(),
                s.getAccountType(),
                s.getExternalReference(),
                s.getStatus(),
                s.getMatchedSettlementId()
        );
    }
}
