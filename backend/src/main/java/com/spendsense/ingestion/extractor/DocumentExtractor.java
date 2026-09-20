package com.spendsense.ingestion.extractor;

import com.spendsense.document.DocumentAccountType;
import com.spendsense.document.DocumentFileType;
import com.spendsense.ingestion.TransactionCandidate;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Strategy interface for extracting transaction candidates from financial document streams.
 */
public interface DocumentExtractor {

    boolean supports(DocumentFileType fileType);

    List<TransactionCandidate> extract(
        UUID documentId,
        DocumentAccountType accountType,
        InputStream stream
    ) throws DocumentExtractionException;
}
