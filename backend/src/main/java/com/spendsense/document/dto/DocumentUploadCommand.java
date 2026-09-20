package com.spendsense.document.dto;

import com.spendsense.document.DocumentAccountType;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.Objects;

public record DocumentUploadCommand(
        String originalFilename,
        DocumentAccountType accountType,
        String institutionName,
        String accountNumberLast4,
        LocalDate statementStartDate,
        LocalDate statementEndDate,
        InputStream contentStream,
        long sizeBytes
) {
    public DocumentUploadCommand {
        Objects.requireNonNull(originalFilename, "originalFilename cannot be null");
        Objects.requireNonNull(accountType, "accountType cannot be null");
        Objects.requireNonNull(contentStream, "contentStream cannot be null");
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be greater than 0");
        }
    }
}
