package com.spendsense.ingestion;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates canonical TransactionCandidate records against financial sanity requirements.
 */
@Component
public class CandidateValidator {

    public CandidateValidationResult validate(TransactionCandidate candidate) {
        List<CandidateValidationError> errors = new ArrayList<>();

        if (candidate.transactionDate() == null) {
            errors.add(new CandidateValidationError(
                "transactionDate",
                CandidateValidationError.CODE_INVALID_DATE,
                "Transaction date is missing or invalid"
            ));
        }

        if (candidate.amount() == null) {
            errors.add(new CandidateValidationError(
                "amount",
                CandidateValidationError.CODE_MISSING_AMOUNT,
                "Transaction amount is missing"
            ));
        } else if (candidate.amount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(new CandidateValidationError(
                "amount",
                CandidateValidationError.CODE_INVALID_AMOUNT,
                "Transaction amount must be strictly positive"
            ));
        }

        if (candidate.direction() == null && (candidate.classification() == null || candidate.classification() == SourceTransactionClassification.UNKNOWN)) {
            errors.add(new CandidateValidationError(
                "direction",
                CandidateValidationError.CODE_MISSING_DIRECTION,
                "Transaction direction or source classification could not be determined"
            ));
        }

        boolean hasDescription = candidate.rawDescription() != null && !candidate.rawDescription().isBlank();
        boolean hasMerchant = candidate.rawMerchant() != null && !candidate.rawMerchant().isBlank();
        if (!hasDescription && !hasMerchant) {
            errors.add(new CandidateValidationError(
                "description",
                CandidateValidationError.CODE_MISSING_DESCRIPTION,
                "Transaction narration or merchant name is missing"
            ));
        }

        if (errors.isEmpty()) {
            return CandidateValidationResult.valid();
        } else {
            return CandidateValidationResult.invalid(errors);
        }
    }
}
