package com.spendsense.ingestion.dedup;

import com.spendsense.document.DocumentAccountType;
import com.spendsense.ingestion.CandidateValidationResult;
import com.spendsense.ingestion.SourceTransactionClassification;
import com.spendsense.ingestion.TransactionCandidate;
import com.spendsense.transaction.PaymentMethod;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionType;
import com.spendsense.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DeduplicationServiceTest {

    private DeduplicationService deduplicationService;
    private User testUser;

    @BeforeEach
    void setUp() {
        deduplicationService = new DeduplicationService();
        testUser = new User("test@spendsense.com", "password123", "Test User", "Asia/Kolkata");
    }

    private Transaction createExistingTransaction(
            LocalDate date,
            BigDecimal amount,
            String merchant,
            TransactionType type,
            String externalRef
    ) {
        Transaction t = new Transaction(
                testUser,
                amount,
                merchant,
                "Description of " + merchant,
                TransactionCategory.FOOD,
                type,
                PaymentMethod.UPI,
                date,
                externalRef != null ? "Ref: " + externalRef : null
        );
        if (externalRef != null) {
            t.setSourceReference("doc:xyz:row:1:" + externalRef);
        }
        return t;
    }

    private TransactionCandidate createCandidate(
            LocalDate date,
            BigDecimal amount,
            String merchant,
            TransactionType direction,
            String externalRef
    ) {
        return new TransactionCandidate(
                UUID.randomUUID(),
                1,
                date,
                merchant,
                merchant + " purchase",
                amount,
                direction,
                SourceTransactionClassification.PURCHASE,
                PaymentMethod.UPI,
                externalRef,
                null,
                DocumentAccountType.SAVINGS,
                "INR",
                "DR",
                CandidateValidationResult.valid()
        );
    }

    @Test
    @DisplayName("Should identify exact duplicate when date, amount, direction, and merchant match")
    void shouldIdentifyExactDuplicateByMerchant() {
        LocalDate date = LocalDate.of(2026, 3, 15);
        BigDecimal amount = new BigDecimal("450.00");
        Transaction existing = createExistingTransaction(date, amount, "Swiggy", TransactionType.EXPENSE, null);
        TransactionCandidate candidate = createCandidate(date, amount, "Swiggy", TransactionType.EXPENSE, null);

        DuplicateCheckResult result = deduplicationService.checkDuplicate(
                candidate, List.of(existing), new HashSet<>());

        assertTrue(result.isExactDuplicate());
        assertEquals(existing, result.matchedExistingTransaction());
    }

    @Test
    @DisplayName("Should identify exact duplicate when external reference matches even if merchant text varies slightly")
    void shouldIdentifyExactDuplicateByReference() {
        LocalDate date = LocalDate.of(2026, 3, 15);
        BigDecimal amount = new BigDecimal("1200.00");
        String ref = "UPI123456789012";
        Transaction existing = createExistingTransaction(date, amount, "Amazon India", TransactionType.EXPENSE, ref);
        TransactionCandidate candidate = createCandidate(date, amount, "AMZN Mktp", TransactionType.EXPENSE, ref);

        DuplicateCheckResult result = deduplicationService.checkDuplicate(
                candidate, List.of(existing), new HashSet<>());

        assertTrue(result.isExactDuplicate());
        assertEquals(existing, result.matchedExistingTransaction());
    }

    @Test
    @DisplayName("Should identify same-document duplicate within the same batch")
    void shouldIdentifySameDocumentDuplicate() {
        LocalDate date = LocalDate.of(2026, 3, 15);
        BigDecimal amount = new BigDecimal("350.00");
        TransactionCandidate candidate1 = createCandidate(date, amount, "Starbucks", TransactionType.EXPENSE, null);
        TransactionCandidate candidate2 = createCandidate(date, amount, "Starbucks", TransactionType.EXPENSE, null);

        Set<String> seen = new HashSet<>();
        DuplicateCheckResult result1 = deduplicationService.checkDuplicate(candidate1, List.of(), seen);
        DuplicateCheckResult result2 = deduplicationService.checkDuplicate(candidate2, List.of(), seen);

        assertFalse(result1.isExactDuplicate());
        assertTrue(result2.isExactDuplicate());
        assertTrue(result2.reason().contains("same statement document"));
    }

    @Test
    @DisplayName("Should identify probable duplicate when date is close, amount matches, and merchant is similar")
    void shouldIdentifyProbableDuplicate() {
        LocalDate existingDate = LocalDate.of(2026, 3, 15);
        LocalDate candidateDate = LocalDate.of(2026, 3, 16); // 1 day difference
        BigDecimal amount = new BigDecimal("850.00");

        Transaction existing = createExistingTransaction(existingDate, amount, "Uber India", TransactionType.EXPENSE, null);
        TransactionCandidate candidate = createCandidate(candidateDate, amount, "Uber Trip Bangalore", TransactionType.EXPENSE, null);

        DuplicateCheckResult result = deduplicationService.checkDuplicate(
                candidate, List.of(existing), new HashSet<>());

        assertTrue(result.isProbableDuplicate());
        assertEquals(existing, result.matchedExistingTransaction());
    }

    @Test
    @DisplayName("Should NOT treat amount + date alone as duplicate when merchants are completely different")
    void shouldNotTreatAmountAndDateAloneAsDuplicate() {
        LocalDate date = LocalDate.of(2026, 3, 15);
        BigDecimal amount = new BigDecimal("500.00");

        Transaction existing = createExistingTransaction(date, amount, "Zomato", TransactionType.EXPENSE, null);
        TransactionCandidate candidate = createCandidate(date, amount, "Shell Petrol", TransactionType.EXPENSE, null);

        DuplicateCheckResult result = deduplicationService.checkDuplicate(
                candidate, List.of(existing), new HashSet<>());

        assertFalse(result.isExactDuplicate());
        assertFalse(result.isProbableDuplicate());
    }

    @Test
    @DisplayName("Should NOT treat different directions as duplicate even if date and amount match")
    void shouldNotTreatOppositeDirectionsAsDuplicate() {
        LocalDate date = LocalDate.of(2026, 3, 15);
        BigDecimal amount = new BigDecimal("1000.00");

        Transaction existing = createExistingTransaction(date, amount, "Transfer", TransactionType.INCOME, null);
        TransactionCandidate candidate = createCandidate(date, amount, "Transfer", TransactionType.EXPENSE, null);

        DuplicateCheckResult result = deduplicationService.checkDuplicate(
                candidate, List.of(existing), new HashSet<>());

        assertFalse(result.isExactDuplicate());
        assertFalse(result.isProbableDuplicate());
    }
}
