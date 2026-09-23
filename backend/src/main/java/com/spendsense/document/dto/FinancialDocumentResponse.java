package com.spendsense.document.dto;

import com.spendsense.document.DocumentAccountType;
import com.spendsense.document.DocumentFileType;
import com.spendsense.document.DocumentStatus;
import com.spendsense.document.FinancialDocument;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FinancialDocumentResponse(
        UUID id,
        String originalFilename,
        DocumentFileType fileType,
        long fileSizeBytes,
        String contentHash,
        String institutionName,
        DocumentAccountType accountType,
        String accountNumberLast4,
        LocalDate statementStartDate,
        LocalDate statementEndDate,
        DocumentStatus status,
        int totalRowsDetected,
        int importedCount,
        int duplicateCount,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        UUID accountId,
        String accountName
) {
    public FinancialDocumentResponse(
            UUID id,
            String originalFilename,
            DocumentFileType fileType,
            long fileSizeBytes,
            String contentHash,
            String institutionName,
            DocumentAccountType accountType,
            String accountNumberLast4,
            LocalDate statementStartDate,
            LocalDate statementEndDate,
            DocumentStatus status,
            int totalRowsDetected,
            int importedCount,
            int duplicateCount,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, originalFilename, fileType, fileSizeBytes, contentHash, institutionName, accountType, accountNumberLast4,
                statementStartDate, statementEndDate, status, totalRowsDetected, importedCount, duplicateCount,
                errorMessage, createdAt, updatedAt, null, null);
    }

    public static FinancialDocumentResponse from(FinancialDocument doc) {
        UUID accId = doc.getAccount() != null ? doc.getAccount().getId() : null;
        String accName = doc.getAccount() != null ? doc.getAccount().getName() : null;
        return new FinancialDocumentResponse(
                doc.getId(),
                doc.getOriginalFilename(),
                doc.getFileType(),
                doc.getFileSizeBytes(),
                doc.getContentHash(),
                doc.getInstitutionName(),
                doc.getAccountType(),
                doc.getAccountNumberLast4(),
                doc.getStatementStartDate(),
                doc.getStatementEndDate(),
                doc.getStatus(),
                doc.getTotalRowsDetected(),
                doc.getImportedCount(),
                doc.getDuplicateCount(),
                doc.getErrorMessage(),
                doc.getCreatedAt(),
                doc.getUpdatedAt(),
                accId,
                accName
        );
    }
}
