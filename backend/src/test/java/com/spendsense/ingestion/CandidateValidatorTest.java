package com.spendsense.ingestion;

import com.spendsense.document.DocumentAccountType;
import com.spendsense.transaction.PaymentMethod;
import com.spendsense.transaction.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CandidateValidatorTest {

    private CandidateValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CandidateValidator();
    }

    @Test
    void validate_validCandidate_returnsValidResult() {
        TransactionCandidate candidate = TransactionCandidate.builder()
            .documentId(UUID.randomUUID())
            .rowIndex(1)
            .transactionDate(LocalDate.of(2026, 8, 25))
            .rawDescription("UPI/123/Swiggy")
            .amount(new BigDecimal("350.00"))
            .direction(TransactionType.EXPENSE)
            .inferredPaymentMethod(PaymentMethod.UPI)
            .accountType(DocumentAccountType.SAVINGS)
            .build();

        CandidateValidationResult result = validator.validate(candidate);
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void validate_missingDate_returnsInvalidDateError() {
        TransactionCandidate candidate = TransactionCandidate.builder()
            .rowIndex(1)
            .rawDescription("Swiggy")
            .amount(new BigDecimal("350.00"))
            .direction(TransactionType.EXPENSE)
            .build();

        CandidateValidationResult result = validator.validate(candidate);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.code().equals(CandidateValidationError.CODE_INVALID_DATE)));
    }

    @Test
    void validate_zeroOrNegativeAmount_returnsInvalidAmountError() {
        TransactionCandidate candidateZero = TransactionCandidate.builder()
            .rowIndex(1)
            .transactionDate(LocalDate.now())
            .rawDescription("Test")
            .amount(BigDecimal.ZERO)
            .direction(TransactionType.EXPENSE)
            .build();

        CandidateValidationResult resultZero = validator.validate(candidateZero);
        assertFalse(resultZero.isValid());
        assertTrue(resultZero.getErrors().stream().anyMatch(e -> e.code().equals(CandidateValidationError.CODE_INVALID_AMOUNT)));

        TransactionCandidate candidateNull = TransactionCandidate.builder()
            .rowIndex(1)
            .transactionDate(LocalDate.now())
            .rawDescription("Test")
            .amount(null)
            .direction(TransactionType.EXPENSE)
            .build();

        CandidateValidationResult resultNull = validator.validate(candidateNull);
        assertFalse(resultNull.isValid());
        assertTrue(resultNull.getErrors().stream().anyMatch(e -> e.code().equals(CandidateValidationError.CODE_MISSING_AMOUNT)));
    }

    @Test
    void validate_missingDirectionAndUnknownClassification_returnsMissingDirectionError() {
        TransactionCandidate candidate = TransactionCandidate.builder()
            .rowIndex(1)
            .transactionDate(LocalDate.now())
            .rawDescription("Test")
            .amount(new BigDecimal("100.00"))
            .direction(null)
            .classification(SourceTransactionClassification.UNKNOWN)
            .build();

        CandidateValidationResult result = validator.validate(candidate);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.code().equals(CandidateValidationError.CODE_MISSING_DIRECTION)));
    }

    @Test
    void validate_knownClassificationWithNullDirection_returnsValidResult() {
        TransactionCandidate candidate = TransactionCandidate.builder()
            .rowIndex(1)
            .transactionDate(LocalDate.now())
            .rawDescription("CREDIT CARD PAYMENT RECEIVED")
            .amount(new BigDecimal("20000.00"))
            .direction(null)
            .classification(SourceTransactionClassification.PAYMENT)
            .build();

        CandidateValidationResult result = validator.validate(candidate);
        assertTrue(result.isValid(), "Candidate with known non-directional classification like PAYMENT must be valid");
    }

    @Test
    void validate_missingDescriptionAndMerchant_returnsMissingDescriptionError() {
        TransactionCandidate candidate = TransactionCandidate.builder()
            .rowIndex(1)
            .transactionDate(LocalDate.now())
            .rawDescription("")
            .rawMerchant(null)
            .amount(new BigDecimal("100.00"))
            .direction(TransactionType.EXPENSE)
            .build();

        CandidateValidationResult result = validator.validate(candidate);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.code().equals(CandidateValidationError.CODE_MISSING_DESCRIPTION)));
    }
}
