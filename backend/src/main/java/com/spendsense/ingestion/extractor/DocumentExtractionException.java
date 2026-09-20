package com.spendsense.ingestion.extractor;

/**
 * Structured exception thrown during statement extraction.
 */
public class DocumentExtractionException extends RuntimeException {

    public enum ErrorCode {
        UNSUPPORTED_FORMAT,
        NO_HEADER_FOUND,
        MISSING_REQUIRED_COLUMNS,
        EMPTY_DOCUMENT,
        AMBIGUOUS_SHEET,
        CORRUPTED_FILE,
        PARSING_ERROR,
        PASSWORD_PROTECTED_DOCUMENT,
        SCANNED_DOCUMENT_UNSUPPORTED
    }

    private final ErrorCode errorCode;

    public DocumentExtractionException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DocumentExtractionException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
