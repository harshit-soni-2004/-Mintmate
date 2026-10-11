package com.spendsense.ingestion.extractor;

import com.spendsense.document.DocumentAccountType;

import java.time.LocalDate;

public record StatementDocumentMetadata(
    String institutionName,
    DocumentAccountType accountType,
    String accountNumberLast4,
    LocalDate statementStartDate,
    LocalDate statementEndDate
) {
    public static StatementDocumentMetadata empty() {
        return new StatementDocumentMetadata(null, null, null, null, null);
    }

    public boolean hasInstitution() {
        return institutionName != null && !institutionName.isBlank();
    }

    public boolean hasAccountNumberLast4() {
        return accountNumberLast4 != null && !accountNumberLast4.isBlank();
    }
}
