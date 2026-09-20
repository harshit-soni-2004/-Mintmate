package com.spendsense.ingestion;

import com.spendsense.document.DocumentAccountType;
import com.spendsense.transaction.PaymentMethod;
import com.spendsense.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Source-agnostic, immutable canonical candidate representation of a financial transaction.
 * Extracted from statements (CSV, XLSX, PDF) or future ingestion channels (SMS, API)
 * before deduplication and final categorization.
 */
public record TransactionCandidate(
    UUID documentId,
    int rowIndex,
    LocalDate transactionDate,
    String rawMerchant,
    String rawDescription,
    BigDecimal amount,
    TransactionType direction,
    SourceTransactionClassification classification,
    PaymentMethod inferredPaymentMethod,
    String externalReference,
    BigDecimal balanceAfter,
    DocumentAccountType accountType,
    String currency,
    String rawTypeIndicator,
    CandidateValidationResult validationResult
) {
    public boolean isValid() {
        return validationResult != null && validationResult.isValid();
    }

    public TransactionCandidate withValidationResult(CandidateValidationResult result) {
        return new TransactionCandidate(
            documentId,
            rowIndex,
            transactionDate,
            rawMerchant,
            rawDescription,
            amount,
            direction,
            classification,
            inferredPaymentMethod,
            externalReference,
            balanceAfter,
            accountType,
            currency,
            rawTypeIndicator,
            result
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID documentId;
        private int rowIndex;
        private LocalDate transactionDate;
        private String rawMerchant;
        private String rawDescription;
        private BigDecimal amount;
        private TransactionType direction;
        private SourceTransactionClassification classification = SourceTransactionClassification.UNKNOWN;
        private PaymentMethod inferredPaymentMethod = PaymentMethod.OTHER;
        private String externalReference;
        private BigDecimal balanceAfter;
        private DocumentAccountType accountType;
        private String currency = "INR";
        private String rawTypeIndicator;
        private CandidateValidationResult validationResult = CandidateValidationResult.valid();

        public Builder documentId(UUID documentId) {
            this.documentId = documentId;
            return this;
        }

        public Builder rowIndex(int rowIndex) {
            this.rowIndex = rowIndex;
            return this;
        }

        public Builder transactionDate(LocalDate transactionDate) {
            this.transactionDate = transactionDate;
            return this;
        }

        public Builder rawMerchant(String rawMerchant) {
            this.rawMerchant = rawMerchant;
            return this;
        }

        public Builder rawDescription(String rawDescription) {
            this.rawDescription = rawDescription;
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder direction(TransactionType direction) {
            this.direction = direction;
            return this;
        }

        public Builder classification(SourceTransactionClassification classification) {
            this.classification = classification;
            return this;
        }

        public Builder inferredPaymentMethod(PaymentMethod inferredPaymentMethod) {
            this.inferredPaymentMethod = inferredPaymentMethod;
            return this;
        }

        public Builder externalReference(String externalReference) {
            this.externalReference = externalReference;
            return this;
        }

        public Builder balanceAfter(BigDecimal balanceAfter) {
            this.balanceAfter = balanceAfter;
            return this;
        }

        public Builder accountType(DocumentAccountType accountType) {
            this.accountType = accountType;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder rawTypeIndicator(String rawTypeIndicator) {
            this.rawTypeIndicator = rawTypeIndicator;
            return this;
        }

        public Builder validationResult(CandidateValidationResult validationResult) {
            this.validationResult = validationResult;
            return this;
        }

        public TransactionCandidate build() {
            return new TransactionCandidate(
                documentId,
                rowIndex,
                transactionDate,
                rawMerchant,
                rawDescription,
                amount,
                direction,
                classification,
                inferredPaymentMethod,
                externalReference,
                balanceAfter,
                accountType,
                currency,
                rawTypeIndicator,
                validationResult
            );
        }
    }
}
