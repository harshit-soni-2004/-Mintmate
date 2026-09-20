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
        Instant updatedAt
) {
    public static FinancialDocumentResponse from(FinancialDocument doc) {
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
                doc.getUpdatedAt()
        );
    }
}
