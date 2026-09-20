package com.spendsense.ingestion;

import java.util.Collections;
import java.util.List;

/**
 * Encapsulates the validation outcome of an extracted TransactionCandidate.
 */
public record CandidateValidationResult(
    boolean isValid,
    List<CandidateValidationError> errors
) {
    public static CandidateValidationResult valid() {
        return new CandidateValidationResult(true, Collections.emptyList());
    }

    public static CandidateValidationResult invalid(List<CandidateValidationError> errors) {
        return new CandidateValidationResult(false, errors != null ? List.copyOf(errors) : Collections.emptyList());
    }

    public static CandidateValidationResult invalid(String field, String code, String message) {
        return new CandidateValidationResult(false, List.of(new CandidateValidationError(field, code, message)));
    }

    public List<CandidateValidationError> getErrors() {
        return errors != null ? errors : Collections.emptyList();
    }

    public String getErrorMessage() {
        if (errors == null || errors.isEmpty()) {
            return "";
        }
        return errors.stream()
                .map(CandidateValidationError::message)
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
    }
}
