package com.spendsense.ingestion.extractor;

import com.spendsense.document.DocumentAccountType;
import com.spendsense.ingestion.CandidateValidator;
import com.spendsense.ingestion.SourceTransactionClassification;
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

    @Test
    void extract_realKotakStatement_whenFilePresent_extractsAllTransactionsWithoutErrors() throws Exception {
        File file = new File("../AccountStatement_01-Jun-2026_31-Aug-2026.pdf");
        if (!file.exists()) {
            file = new File("AccountStatement_01-Jun-2026_31-Aug-2026.pdf");
        }
        Assumptions.assumeTrue(file.exists(), "Kotak PDF not present; skipping local regression test.");

        PdfStatementExtractor extractor = new PdfStatementExtractor(new StatementNormalizer(), new CandidateValidator());
        List<TransactionCandidate> candidates;
        try (InputStream is = new FileInputStream(file)) {
            candidates = extractor.extract(UUID.randomUUID(), DocumentAccountType.SAVINGS, is);
        }

        assertEquals(11, candidates.size(), "Should extract exactly 11 transactions from Kotak statement");

        long validCount = candidates.stream().filter(c -> c.validationResult().isValid()).count();
        assertEquals(11, validCount, "All 11 extracted candidates must be valid");


        // 1. 10 Aug 2026 - IFT-VEEFIN SOLUTIONS LIMITED - deposit 46,200.00 - bal 46,200.00
        TransactionCandidate c1 = candidates.get(0);
        assertEquals(LocalDate.of(2026, 8, 10), c1.transactionDate());
        assertEquals(new BigDecimal("46200.00"), c1.amount());
        assertEquals(TransactionType.INCOME, c1.direction());
        assertEquals(new BigDecimal("46200.00"), c1.balanceAfter());
        assertEquals("FCM-260810OITIT0", c1.externalReference());
        assertTrue(c1.rawDescription().contains("IFT-VEEFIN SOLUTIONS LIMITED"));
        assertTrue(c1.rawDescription().contains("260810OITIT0"));

        // 2. 10 Aug 2026 - UPI/HARSHIT ASHOK S/658828783145/UPI - withdrawal 15,000.00 - bal 31,200.00
        TransactionCandidate c2 = candidates.get(1);
        assertEquals(LocalDate.of(2026, 8, 10), c2.transactionDate());
        assertEquals(new BigDecimal("15000.00"), c2.amount());
        assertEquals(TransactionType.EXPENSE, c2.direction());
        assertEquals(new BigDecimal("31200.00"), c2.balanceAfter());
        assertEquals("UPI-210017231015", c2.externalReference());

        // 3. 10 Aug 2026 - ATW/3042/+Manish Nagar AndheriWMumbaiM100826/20:53 - withdrawal 10,000.00 - bal 21,200.00
        TransactionCandidate c3 = candidates.get(2);
        assertEquals(LocalDate.of(2026, 8, 10), c3.transactionDate());
        assertEquals(new BigDecimal("10000.00"), c3.amount());
        assertEquals(TransactionType.CASH_WITHDRAWAL, c3.direction());
        assertEquals(new BigDecimal("21200.00"), c3.balanceAfter());
        assertEquals("622215192388", c3.externalReference());
        assertTrue(c3.rawDescription().contains("AndheriWMumbaiM100826/20:53"));

        // 4. 11 Aug 2026 - CASHBACK EARNED - deposit 6.00 - bal 21,206.00 (Refund per Phase 7C semantics)
        TransactionCandidate c4 = candidates.get(3);
        assertEquals(LocalDate.of(2026, 8, 11), c4.transactionDate());
        assertEquals(new BigDecimal("6.00"), c4.amount());
        assertEquals(SourceTransactionClassification.REFUND, c4.classification());
        assertTrue(c4.direction() == null, "Refund direction is null per Phase 7C semantics");
        assertEquals(new BigDecimal("21206.00"), c4.balanceAfter());
        assertEquals("ONBF-b24391b1d182b0b", c4.externalReference());
        assertEquals("CASHBACK EARNED", c4.rawDescription());

        // 10. 29 Aug 2026 - UPI withdrawal 1250.00
        TransactionCandidate c10 = candidates.get(9);
        assertEquals(LocalDate.of(2026, 8, 29), c10.transactionDate());
        assertEquals(new BigDecimal("1250.00"), c10.amount());
        assertEquals(TransactionType.EXPENSE, c10.direction());
        assertEquals(new BigDecimal("7671.54"), c10.balanceAfter());

        // 11. 29 Aug 2026 - CASHBACK EARNED - deposit 1.00 - bal 7672.54
        TransactionCandidate c11 = candidates.get(10);
        assertEquals(LocalDate.of(2026, 8, 29), c11.transactionDate());
        assertEquals(new BigDecimal("1.00"), c11.amount());
        assertEquals(SourceTransactionClassification.REFUND, c11.classification());
        assertEquals(new BigDecimal("7672.54"), c11.balanceAfter());
        assertEquals("ONBF-3778ad596435489", c11.externalReference());
        assertEquals("CASHBACK EARNED", c11.rawDescription());
    }
}
