package com.spendsense.categorization;

import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "categorization_feedbacks")
public class CategorizationFeedback {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @Column(name = "raw_merchant", length = 120)
    private String rawMerchant;

    @Column(name = "normalized_merchant", nullable = false, length = 120)
    private String normalizedMerchant;

    @Column(name = "raw_description", length = 255)
    private String rawDescription;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "predicted_category", length = 32)
    private TransactionCategory predictedCategory;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_level", length = 16)
    private ConfidenceLevel confidenceLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_category", nullable = false, length = 32)
    private TransactionCategory finalCategory;

    @Column(name = "is_corrected", nullable = false)
    private boolean corrected;

    @Enumerated(EnumType.STRING)
    @Column(name = "prediction_source", nullable = false, length = 32)
    private CategorizationSource predictionSource;

    @Column(name = "model_version", length = 64)
    private String modelVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CategorizationFeedback() {
    }

    public CategorizationFeedback(
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
            String modelVersion
    ) {
        this.id = UUID.randomUUID();
        this.user = Objects.requireNonNull(user, "User cannot be null");
        this.transaction = transaction;
        this.rawMerchant = rawMerchant;
        this.normalizedMerchant = Objects.requireNonNull(normalizedMerchant, "Normalized merchant cannot be null");
        this.rawDescription = rawDescription;
        this.amount = amount;
        this.predictedCategory = predictedCategory;
        this.confidence = confidence;
        this.confidenceLevel = confidenceLevel;
        this.finalCategory = Objects.requireNonNull(finalCategory, "Final category cannot be null");
        this.predictionSource = Objects.requireNonNull(predictionSource, "Prediction source cannot be null");
        this.modelVersion = modelVersion;
        this.corrected = predictedCategory != null && predictedCategory != finalCategory;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public String getRawMerchant() {
        return rawMerchant;
    }

    public String getNormalizedMerchant() {
        return normalizedMerchant;
    }

    public String getRawDescription() {
        return rawDescription;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionCategory getPredictedCategory() {
        return predictedCategory;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public ConfidenceLevel getConfidenceLevel() {
        return confidenceLevel;
    }

    public TransactionCategory getFinalCategory() {
        return finalCategory;
    }

    public boolean isCorrected() {
        return corrected;
    }

    public CategorizationSource getPredictionSource() {
        return predictionSource;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
