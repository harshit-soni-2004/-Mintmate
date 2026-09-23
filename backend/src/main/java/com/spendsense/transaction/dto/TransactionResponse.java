package com.spendsense.transaction.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.categorization.CategorizationSource;
import com.spendsense.categorization.ConfidenceLevel;
import com.spendsense.transaction.PaymentMethod;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        BigDecimal amount,
        String merchant,
        String description,
        TransactionCategory category,
        TransactionType transactionType,
        PaymentMethod paymentMethod,
        LocalDate transactionDate,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        Double confidence,
        ConfidenceLevel confidenceLevel,
        CategorizationSource categorizationSource,
        Boolean requiresUserConfirmation,
        TransactionCategory predictedCategory,
        List<CategorizationCandidateDto> alternatives,
        com.spendsense.transaction.TransactionSourceType sourceType,
        UUID sourceDocumentId,
        String sourceReference,
        UUID accountId,
        String accountName,
        com.spendsense.transaction.TransferDirection transferDirection,
        UUID counterpartyAccountId,
        String counterpartyAccountName,
        UUID linkedTransactionId,
        com.spendsense.transaction.TransferMatchStatus transferMatchStatus
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public TransactionResponse(
            UUID id,
            BigDecimal amount,
            String merchant,
            String description,
            TransactionCategory category,
            TransactionType transactionType,
            PaymentMethod paymentMethod,
            LocalDate transactionDate,
            String notes,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, amount, merchant, description, category, transactionType, paymentMethod, transactionDate, notes,
                createdAt, updatedAt, null, null, null, false, null, List.of(),
                com.spendsense.transaction.TransactionSourceType.MANUAL, null, null,
                null, null, null, null, null, null, com.spendsense.transaction.TransferMatchStatus.NONE);
    }

    public TransactionResponse(
            UUID id,
            BigDecimal amount,
            String merchant,
            String description,
            TransactionCategory category,
            TransactionType transactionType,
            PaymentMethod paymentMethod,
            LocalDate transactionDate,
            String notes,
            Instant createdAt,
            Instant updatedAt,
            Double confidence,
            ConfidenceLevel confidenceLevel,
            CategorizationSource categorizationSource,
            Boolean requiresUserConfirmation,
            TransactionCategory predictedCategory,
            List<CategorizationCandidateDto> alternatives,
            com.spendsense.transaction.TransactionSourceType sourceType,
            UUID sourceDocumentId,
            String sourceReference
    ) {
        this(id, amount, merchant, description, category, transactionType, paymentMethod, transactionDate, notes,
                createdAt, updatedAt, confidence, confidenceLevel, categorizationSource, requiresUserConfirmation,
                predictedCategory, alternatives, sourceType, sourceDocumentId, sourceReference,
                null, null, null, null, null, null, com.spendsense.transaction.TransferMatchStatus.NONE);
    }

    public static TransactionResponse from(Transaction transaction) {
        return from(transaction, null);
    }

    public static TransactionResponse from(Transaction transaction, List<CategorizationCandidateDto> explicitAlternatives) {
        List<CategorizationCandidateDto> alts = explicitAlternatives;
        if (alts == null && transaction.getCategorizationAlternatives() != null && !transaction.getCategorizationAlternatives().isBlank()) {
            try {
                alts = OBJECT_MAPPER.readValue(transaction.getCategorizationAlternatives(), new TypeReference<List<CategorizationCandidateDto>>() {});
            } catch (Exception e) {
                alts = Collections.emptyList();
            }
        }
        if (alts == null) {
            alts = Collections.emptyList();
        }

        Double conf = transaction.getCategorizationConfidence() != null
                ? transaction.getCategorizationConfidence().doubleValue()
                : null;

        com.spendsense.transaction.TransactionSourceType srcType = transaction.getSourceType() != null
                ? transaction.getSourceType()
                : com.spendsense.transaction.TransactionSourceType.MANUAL;
        UUID docId = transaction.getSourceDocument() != null
                ? transaction.getSourceDocument().getId()
                : null;

        UUID accId = transaction.getAccount() != null ? transaction.getAccount().getId() : null;
        String accName = transaction.getAccount() != null ? transaction.getAccount().getName() : null;
        UUID cpAccId = transaction.getCounterpartyAccount() != null ? transaction.getCounterpartyAccount().getId() : null;
        String cpAccName = transaction.getCounterpartyAccount() != null ? transaction.getCounterpartyAccount().getName() : null;
        UUID linkedTxId = transaction.getLinkedTransaction() != null ? transaction.getLinkedTransaction().getId() : null;

        return new TransactionResponse(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getMerchant(),
                transaction.getDescription(),
                transaction.getCategory(),
                transaction.getTransactionType(),
                transaction.getPaymentMethod(),
                transaction.getTransactionDate(),
                transaction.getNotes(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt(),
                conf,
                transaction.getCategorizationConfidenceLevel(),
                transaction.getCategorizationSource(),
                transaction.isRequiresReview(),
                transaction.getPredictedCategory(),
                alts,
                srcType,
                docId,
                transaction.getSourceReference(),
                accId,
                accName,
                transaction.getTransferDirection(),
                cpAccId,
                cpAccName,
                linkedTxId,
                transaction.getTransferMatchStatus() != null ? transaction.getTransferMatchStatus() : com.spendsense.transaction.TransferMatchStatus.NONE
        );
    }
}
