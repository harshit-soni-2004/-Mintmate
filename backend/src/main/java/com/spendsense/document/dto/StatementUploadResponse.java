package com.spendsense.document.dto;

import com.spendsense.ingestion.service.StatementImportResult;

/**
 * Combined response returned upon statement upload and ingestion.
 */
public record StatementUploadResponse(
        FinancialDocumentResponse document,
        StatementImportResult importResult
) {
}
