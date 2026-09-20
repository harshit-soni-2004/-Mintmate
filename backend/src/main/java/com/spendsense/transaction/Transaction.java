package com.spendsense.transaction;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 120)
    private String merchant;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TransactionCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 16)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 32)
    private PaymentMethod paymentMethod;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(length = 1000)
    private String notes;

    @Column(name = "categorization_confidence", precision = 5, scale = 4)
    private BigDecimal categorizationConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "categorization_confidence_level", length = 16)
    private com.spendsense.categorization.ConfidenceLevel categorizationConfidenceLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "categorization_source", length = 32)
    private com.spendsense.categorization.CategorizationSource categorizationSource;

    @Column(name = "requires_review", nullable = false)
    private boolean requiresReview;

    @Enumerated(EnumType.STRING)
    @Column(name = "predicted_category", length = 32)
    private TransactionCategory predictedCategory;

    @Column(name = "categorization_alternatives", length = 1000)
    private String categorizationAlternatives;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private TransactionSourceType sourceType = TransactionSourceType.MANUAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id")
    private com.spendsense.document.FinancialDocument sourceDocument;

    @Column(name = "source_reference", length = 120)
    private String sourceReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transaction() {
    }

    public Transaction(User user, BigDecimal amount, String merchant, TransactionCategory category,
                       TransactionType transactionType, LocalDate transactionDate, String notes) {
        this(user, amount, merchant, null, category, transactionType, null, transactionDate, notes);
    }

    public Transaction(User user, BigDecimal amount, String merchant, String description,
                       TransactionCategory category, TransactionType transactionType,
                       PaymentMethod paymentMethod, LocalDate transactionDate, String notes) {
        this(user, amount, merchant, description, category, transactionType, paymentMethod, transactionDate, notes,
                null, null, null, false, null, null);
    }

    public Transaction(User user, BigDecimal amount, String merchant, String description,
                       TransactionCategory category, TransactionType transactionType,
                       PaymentMethod paymentMethod, LocalDate transactionDate, String notes,
                       BigDecimal categorizationConfidence, com.spendsense.categorization.ConfidenceLevel categorizationConfidenceLevel,
                       com.spendsense.categorization.CategorizationSource categorizationSource, boolean requiresReview,
                       TransactionCategory predictedCategory, String categorizationAlternatives) {
        this(user, amount, merchant, description, category, transactionType, paymentMethod, transactionDate, notes,
                categorizationConfidence, categorizationConfidenceLevel, categorizationSource, requiresReview,
                predictedCategory, categorizationAlternatives, TransactionSourceType.MANUAL, null, null);
    }

    public Transaction(User user, BigDecimal amount, String merchant, String description,
                       TransactionCategory category, TransactionType transactionType,
                       PaymentMethod paymentMethod, LocalDate transactionDate, String notes,
                       BigDecimal categorizationConfidence, com.spendsense.categorization.ConfidenceLevel categorizationConfidenceLevel,
                       com.spendsense.categorization.CategorizationSource categorizationSource, boolean requiresReview,
                       TransactionCategory predictedCategory, String categorizationAlternatives,
                       TransactionSourceType sourceType, com.spendsense.document.FinancialDocument sourceDocument,
                       String sourceReference) {
        this.id = UUID.randomUUID();
        this.user = user;
        update(amount, merchant, description, category, transactionType, paymentMethod, transactionDate, notes);
        this.categorizationConfidence = categorizationConfidence;
        this.categorizationConfidenceLevel = categorizationConfidenceLevel;
        this.categorizationSource = categorizationSource;
        this.requiresReview = requiresReview;
        this.predictedCategory = predictedCategory;
        this.categorizationAlternatives = categorizationAlternatives;
        this.sourceType = sourceType != null ? sourceType : TransactionSourceType.MANUAL;
        this.sourceDocument = sourceDocument;
        this.sourceReference = sourceReference;
    }

    public void update(BigDecimal amount, String merchant, TransactionCategory category,
                       TransactionType transactionType, LocalDate transactionDate, String notes) {
        update(amount, merchant, this.description, category, transactionType, this.paymentMethod, transactionDate, notes);
    }

    public void update(BigDecimal amount, String merchant, String description,
                       TransactionCategory category, TransactionType transactionType,
                       PaymentMethod paymentMethod, LocalDate transactionDate, String notes) {
        this.amount = amount;
        this.merchant = merchant;
        this.description = description;
        this.category = category;
        this.transactionType = transactionType;
        this.paymentMethod = paymentMethod;
        this.transactionDate = transactionDate;
        this.notes = notes;
    }

    public void setCategory(TransactionCategory category) {
        this.category = category;
    }

    public void setRequiresReview(boolean requiresReview) {
        this.requiresReview = requiresReview;
    }

    public void setCategorizationSource(com.spendsense.categorization.CategorizationSource categorizationSource) {
        this.categorizationSource = categorizationSource;
    }

    public void setPredictedCategory(TransactionCategory predictedCategory) {
        this.predictedCategory = predictedCategory;
    }

    public void setCategorizationConfidence(BigDecimal categorizationConfidence) {
        this.categorizationConfidence = categorizationConfidence;
    }

    public void setCategorizationConfidenceLevel(com.spendsense.categorization.ConfidenceLevel categorizationConfidenceLevel) {
        this.categorizationConfidenceLevel = categorizationConfidenceLevel;
    }

    public void setCategorizationAlternatives(String categorizationAlternatives) {
        this.categorizationAlternatives = categorizationAlternatives;
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public BigDecimal getAmount() { return amount; }
    public String getMerchant() { return merchant; }
    public String getDescription() { return description; }
    public TransactionCategory getCategory() { return category; }
    public TransactionType getTransactionType() { return transactionType; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public String getNotes() { return notes; }
    public BigDecimal getCategorizationConfidence() { return categorizationConfidence; }
    public com.spendsense.categorization.ConfidenceLevel getCategorizationConfidenceLevel() { return categorizationConfidenceLevel; }
    public com.spendsense.categorization.CategorizationSource getCategorizationSource() { return categorizationSource; }
    public boolean isRequiresReview() { return requiresReview; }
    public TransactionCategory getPredictedCategory() { return predictedCategory; }
    public String getCategorizationAlternatives() { return categorizationAlternatives; }
    public TransactionSourceType getSourceType() { return sourceType; }
    public void setSourceType(TransactionSourceType sourceType) { this.sourceType = sourceType; }
    public com.spendsense.document.FinancialDocument getSourceDocument() { return sourceDocument; }
    public void setSourceDocument(com.spendsense.document.FinancialDocument sourceDocument) { this.sourceDocument = sourceDocument; }
    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String sourceReference) { this.sourceReference = sourceReference; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
