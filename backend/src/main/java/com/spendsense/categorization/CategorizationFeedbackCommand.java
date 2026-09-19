package com.spendsense.categorization;

import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.user.User;
import java.math.BigDecimal;

public record CategorizationFeedbackCommand(
        User user,
        Transaction transaction,
        String rawMerchant,
        String normalizedMerchant,
        String rawDescription,
        BigDecimal amount,
        TransactionCategory predictedCategory,
        BigDecimal confidence,
        ConfidenceLevel confidenceLevel,
        TransactionCategory finalCategory,
        CategorizationSource predictionSource,
        String modelVersion,
        boolean updateMemory
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private User user;
        private Transaction transaction;
        private String rawMerchant;
        private String normalizedMerchant;
        private String rawDescription;
        private BigDecimal amount;
        private TransactionCategory predictedCategory;
        private BigDecimal confidence;
        private ConfidenceLevel confidenceLevel;
        private TransactionCategory finalCategory;
        private CategorizationSource predictionSource = CategorizationSource.MANUAL;
        private String modelVersion;
        private boolean updateMemory = true;

        public Builder user(User user) {
            this.user = user;
            return this;
        }

        public Builder transaction(Transaction transaction) {
            this.transaction = transaction;
            return this;
        }

        public Builder rawMerchant(String rawMerchant) {
            this.rawMerchant = rawMerchant;
            return this;
        }

        public Builder normalizedMerchant(String normalizedMerchant) {
            this.normalizedMerchant = normalizedMerchant;
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

        public Builder predictedCategory(TransactionCategory predictedCategory) {
            this.predictedCategory = predictedCategory;
            return this;
        }

        public Builder confidence(BigDecimal confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder confidenceLevel(ConfidenceLevel confidenceLevel) {
            this.confidenceLevel = confidenceLevel;
            return this;
        }

        public Builder finalCategory(TransactionCategory finalCategory) {
            this.finalCategory = finalCategory;
            return this;
        }

        public Builder predictionSource(CategorizationSource predictionSource) {
            this.predictionSource = predictionSource;
            return this;
        }

        public Builder modelVersion(String modelVersion) {
            this.modelVersion = modelVersion;
            return this;
        }

        public Builder updateMemory(boolean updateMemory) {
            this.updateMemory = updateMemory;
            return this;
        }

        public CategorizationFeedbackCommand build() {
            return new CategorizationFeedbackCommand(
                    user,
                    transaction,
                    rawMerchant,
                    normalizedMerchant,
                    rawDescription,
                    amount,
                    predictedCategory,
                    confidence,
                    confidenceLevel,
                    finalCategory,
                    predictionSource,
                    modelVersion,
                    updateMemory
            );
        }
    }
}
