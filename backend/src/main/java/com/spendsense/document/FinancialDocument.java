package com.spendsense.document;

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

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "financial_documents")
public class FinancialDocument {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 16)
    private DocumentFileType fileType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String contentHash;

    @Column(name = "institution_name", length = 100)
    private String institutionName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 32)
    private DocumentAccountType accountType;

    @Column(name = "account_number_last4", length = 4)
    private String accountNumberLast4;

    @Column(name = "statement_start_date")
    private LocalDate statementStartDate;

    @Column(name = "statement_end_date")
    private LocalDate statementEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentStatus status;

    @Column(name = "total_rows_detected", nullable = false)
    private int totalRowsDetected;

    @Column(name = "imported_count", nullable = false)
    private int importedCount;

    @Column(name = "duplicate_count", nullable = false)
    private int duplicateCount;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FinancialDocument() {
    }

    public FinancialDocument(
            User user,
            String originalFilename,
            DocumentFileType fileType,
            long fileSizeBytes,
            String storageKey,
            String contentHash,
            String institutionName,
            DocumentAccountType accountType,
            String accountNumberLast4,
            LocalDate statementStartDate,
            LocalDate statementEndDate
    ) {
        this.id = UUID.randomUUID();
        this.user = Objects.requireNonNull(user, "User cannot be null");
        this.originalFilename = Objects.requireNonNull(originalFilename, "Original filename cannot be null");
        this.fileType = Objects.requireNonNull(fileType, "File type cannot be null");
        this.fileSizeBytes = fileSizeBytes;
        this.storageKey = Objects.requireNonNull(storageKey, "Storage key cannot be null");
        this.contentHash = Objects.requireNonNull(contentHash, "Content hash cannot be null");
        this.institutionName = institutionName;
        this.accountType = Objects.requireNonNull(accountType, "Account type cannot be null");
        this.accountNumberLast4 = accountNumberLast4;
        this.statementStartDate = statementStartDate;
        this.statementEndDate = statementEndDate;
        this.status = DocumentStatus.UPLOADED;
        this.totalRowsDetected = 0;
        this.importedCount = 0;
        this.duplicateCount = 0;
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
            status = DocumentStatus.UPLOADED;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void updateProcessingStats(int totalRowsDetected, int importedCount, int duplicateCount) {
        this.totalRowsDetected = totalRowsDetected;
        this.importedCount = importedCount;
        this.duplicateCount = duplicateCount;
    }

    public void updateStatementDates(LocalDate statementStartDate, LocalDate statementEndDate) {
        if (this.statementStartDate == null && statementStartDate != null) {
            this.statementStartDate = statementStartDate;
        }
        if (this.statementEndDate == null && statementEndDate != null) {
            this.statementEndDate = statementEndDate;
        }
    }

    public void markParsing() {
        this.status = DocumentStatus.PARSING;
    }

    public void markCompleted(int totalRowsDetected, int importedCount, int duplicateCount) {
        this.status = DocumentStatus.COMPLETED;
        this.totalRowsDetected = totalRowsDetected;
        this.importedCount = importedCount;
        this.duplicateCount = duplicateCount;
    }

    public void markPartiallyProcessed(int totalRowsDetected, int importedCount, int duplicateCount, String errorMessage) {
        this.status = DocumentStatus.PARTIALLY_PROCESSED;
        this.totalRowsDetected = totalRowsDetected;
        this.importedCount = importedCount;
        this.duplicateCount = duplicateCount;
        this.errorMessage = errorMessage;
    }

    public void markFailed(String errorMessage) {
        this.status = DocumentStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getOriginalFilename() { return originalFilename; }
    public DocumentFileType getFileType() { return fileType; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public String getStorageKey() { return storageKey; }
    public String getContentHash() { return contentHash; }
    public String getInstitutionName() { return institutionName; }
    public DocumentAccountType getAccountType() { return accountType; }
    public String getAccountNumberLast4() { return accountNumberLast4; }
    public LocalDate getStatementStartDate() { return statementStartDate; }
    public LocalDate getStatementEndDate() { return statementEndDate; }
    public DocumentStatus getStatus() { return status; }
    public int getTotalRowsDetected() { return totalRowsDetected; }
    public int getImportedCount() { return importedCount; }
    public int getDuplicateCount() { return duplicateCount; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
