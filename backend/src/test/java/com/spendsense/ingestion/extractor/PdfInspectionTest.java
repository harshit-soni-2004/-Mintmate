package com.spendsense.ingestion.extractor;

import com.spendsense.document.DocumentAccountType;
import com.spendsense.ingestion.CandidateValidator;
import com.spendsense.ingestion.StatementNormalizer;
import com.spendsense.ingestion.TransactionCandidate;
import com.spendsense.transaction.TransactionType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfInspectionTest {

    @Test
    void extract_realSbiStatement_whenFilePresent_extractsAllTransactionsWithoutErrors() throws Exception {
        File file = new File("../AccountStatement_20092026_25810.pdf");
        if (!file.exists()) {
            file = new File("AccountStatement_20092026_25810.pdf");
        }
        Assumptions.assumeTrue(file.exists(), "Real SBI statement PDF not present; skipping local regression test.");

        PdfStatementExtractor extractor = new PdfStatementExtractor(new StatementNormalizer(), new CandidateValidator());
        List<TransactionCandidate> candidates;
        try (InputStream is = new FileInputStream(file)) {
            candidates = extractor.extract(UUID.randomUUID(), DocumentAccountType.SAVINGS, is);
        }

        assertEquals(661, candidates.size(), "Should extract exactly 661 transactions from real SBI statement");

        long validCount = candidates.stream().filter(c -> c.validationResult().isValid()).count();
        assertEquals(661, validCount, "All 661 extracted candidates must be valid");

        // Verify first transaction
        TransactionCandidate first = candidates.get(0);
        assertEquals(LocalDate.of(2026, 4, 1), first.transactionDate());
        assertEquals(new BigDecimal("15.00"), first.amount());
        assertEquals(TransactionType.EXPENSE, first.direction());
        assertEquals(new BigDecimal("1214.47"), first.balanceAfter());
        assertTrue(first.rawDescription().contains("WDL TFR"));
        assertTrue(first.rawDescription().contains("UPI/DR/609162490365/RAJAN"));

        // Verify a credit transaction (index 6, row 7)
        TransactionCandidate credit = candidates.get(6);
        assertEquals(LocalDate.of(2026, 4, 2), credit.transactionDate());
        assertEquals(new BigDecimal("11800.00"), credit.amount());
        assertEquals(TransactionType.INCOME, credit.direction());
        assertEquals(new BigDecimal("12387.47"), credit.balanceAfter());
        assertTrue(credit.rawDescription().contains("DEP TFR"));
        assertTrue(credit.rawDescription().contains("NEFT*IDFB0010201*IDFBH26092"));

        // Verify last transaction (index 660, row 661)
        TransactionCandidate last = candidates.get(660);
        assertEquals(LocalDate.of(2026, 9, 19), last.transactionDate());
        assertEquals(new BigDecimal("50.00"), last.amount());
        assertEquals(TransactionType.EXPENSE, last.direction());
        assertEquals(new BigDecimal("8165.69"), last.balanceAfter());
        assertTrue(last.rawDescription().contains("UPI"), () -> "Actual desc: " + last.rawDescription());
    }
}
