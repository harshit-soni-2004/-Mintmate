package com.spendsense.ingestion;

/**
 * Structured validation error for an individual transaction candidate field.
 */
public record CandidateValidationError(
    String field,
    String code,
    String message
) {
    public static final String CODE_INVALID_DATE = "INVALID_DATE";
    public static final String CODE_MISSING_AMOUNT = "MISSING_AMOUNT";
    public static final String CODE_INVALID_AMOUNT = "INVALID_AMOUNT";
    public static final String CODE_MISSING_DIRECTION = "MISSING_DIRECTION";
    public static final String CODE_MISSING_DESCRIPTION = "MISSING_DESCRIPTION";
}
