package com.spendsense.ingestion.settlement;

import com.spendsense.document.DocumentAccountType;
import com.spendsense.document.FinancialDocument;
import com.spendsense.ingestion.SourceTransactionClassification;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing an internal settlement (e.g. credit-card bill payment),
 * transfer between user accounts, or merchant refund.
 * Preserves document traceability and prevents premature conversion into ordinary income or expense.
 */
@Entity
@Table(name = "statement_settlements")
public class StatementSettlement {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private FinancialDocument document;

    @Column(name = "row_index", nullable = false)
    private int rowIndex;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "raw_merchant", length = 255)
    private String rawMerchant;

    @Column(name = "raw_description", length = 500)
    private String rawDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SourceTransactionClassification classification;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 32)
    private DocumentAccountType accountType;

    @Column(name = "external_reference", length = 120)
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SettlementStatus status;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "matched_settlement_id", length = 36)
    private UUID matchedSettlementId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StatementSettlement() {
    }

    public StatementSettlement(
            User user,
            FinancialDocument document,
            int rowIndex,
            LocalDate transactionDate,
            BigDecimal amount,
            String rawMerchant,
            String rawDescription,
            SourceTransactionClassification classification,
            DocumentAccountType accountType,
            String externalReference,
            SettlementStatus status
    ) {
        this.id = UUID.randomUUID();
        this.user = Objects.requireNonNull(user, "User cannot be null");
        this.document = Objects.requireNonNull(document, "Document cannot be null");
        this.rowIndex = rowIndex;
        this.transactionDate = Objects.requireNonNull(transactionDate, "Transaction date cannot be null");
        this.amount = Objects.requireNonNull(amount, "Amount cannot be null");
        this.rawMerchant = rawMerchant;
        this.rawDescription = rawDescription;
        this.classification = Objects.requireNonNull(classification, "Classification cannot be null");
        this.accountType = Objects.requireNonNull(accountType, "Account type cannot be null");
        this.externalReference = externalReference;
        this.status = status != null ? status : SettlementStatus.UNRESOLVED;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = SettlementStatus.UNRESOLVED;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void markResolved(UUID matchedSettlementId) {
        this.status = SettlementStatus.RESOLVED;
        this.matchedSettlementId = matchedSettlementId;
    }

    public void markUnresolved() {
        this.status = SettlementStatus.UNRESOLVED;
        this.matchedSettlementId = null;
    }

    public void markDiscarded() {
        this.status = SettlementStatus.DISCARDED;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public FinancialDocument getDocument() { return document; }
    public int getRowIndex() { return rowIndex; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public BigDecimal getAmount() { return amount; }
    public String getRawMerchant() { return rawMerchant; }
    public String getRawDescription() { return rawDescription; }
    public SourceTransactionClassification getClassification() { return classification; }
    public DocumentAccountType getAccountType() { return accountType; }
    public String getExternalReference() { return externalReference; }
    public SettlementStatus getStatus() { return status; }
    public UUID getMatchedSettlementId() { return matchedSettlementId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
