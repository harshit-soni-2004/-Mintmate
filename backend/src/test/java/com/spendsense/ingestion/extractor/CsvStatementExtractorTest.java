package com.spendsense.ingestion.extractor;

import com.spendsense.document.DocumentAccountType;
import com.spendsense.document.DocumentFileType;
import com.spendsense.ingestion.CandidateValidationError;
import com.spendsense.ingestion.CandidateValidator;
import com.spendsense.ingestion.SourceTransactionClassification;
import com.spendsense.ingestion.StatementNormalizer;
import com.spendsense.ingestion.TransactionCandidate;
import com.spendsense.transaction.PaymentMethod;
import com.spendsense.transaction.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CsvStatementExtractorTest {

    private CsvStatementExtractor extractor;
    private final UUID documentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        StatementNormalizer normalizer = new StatementNormalizer();
        CandidateValidator validator = new CandidateValidator();
        extractor = new CsvStatementExtractor(normalizer, validator);
    }

    @Test
    void supports_onlyCsv() {
        assertTrue(extractor.supports(DocumentFileType.CSV));
        assertFalse(extractor.supports(DocumentFileType.XLSX));
        assertFalse(extractor.supports(DocumentFileType.PDF));
    }

    @Test
    void extract_hdfcStyleStatementWithMetadataAndFooter() {
        String csv = """
            Account Number: 50100234567890
            Statement Period: 01/08/2026 to 31/08/2026
            Branch: Indiranagar Bangalore
            
            Date,Narration,Chq./Ref.No.,Value Dt,Withdrawal Amt.,Deposit Amt.,Closing Balance
            01/08/2026,UPI/423456789012/Swiggy Bangalore/swiggy@icici,423456789012,01/08/2026,450.00,,52300.50
            05/08/2026,NEFT CR-N12345678-TECH CORP SALARY,N12345678,05/08/2026,,85000.00,137300.50
            12/08/2026,ATM WDL CASH 5000 BANGALORE,TXN998877,12/08/2026,5000.00,,132300.50
            
            Total Withdrawals: 5450.00, Total Deposits: 85000.00
            *** End of Statement ***
            """;

        ByteArrayInputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        List<TransactionCandidate> candidates = extractor.extract(documentId, DocumentAccountType.SAVINGS, stream);

        assertEquals(3, candidates.size());

        // 1. Swiggy expense via UPI
        TransactionCandidate c1 = candidates.get(0);
        assertTrue(c1.isValid());
        assertEquals(LocalDate.of(2026, 8, 1), c1.transactionDate());
        assertEquals(new BigDecimal("450.00"), c1.amount());
        assertEquals(TransactionType.EXPENSE, c1.direction());
        assertEquals(PaymentMethod.UPI, c1.inferredPaymentMethod());
        assertEquals("423456789012", c1.externalReference());
        assertEquals(new BigDecimal("52300.50"), c1.balanceAfter());

        // 2. Salary income via Bank Transfer
        TransactionCandidate c2 = candidates.get(1);
        assertTrue(c2.isValid());
        assertEquals(LocalDate.of(2026, 8, 5), c2.transactionDate());
        assertEquals(new BigDecimal("85000.00"), c2.amount());
        assertEquals(TransactionType.INCOME, c2.direction());
        assertEquals(PaymentMethod.BANK_TRANSFER, c2.inferredPaymentMethod());
        assertEquals("N12345678", c2.externalReference());
        assertEquals(new BigDecimal("137300.50"), c2.balanceAfter());

        // 3. Cash withdrawal
        TransactionCandidate c3 = candidates.get(2);
        assertTrue(c3.isValid());
        assertEquals(LocalDate.of(2026, 8, 12), c3.transactionDate());
        assertEquals(new BigDecimal("5000.00"), c3.amount());
        assertEquals(TransactionType.EXPENSE, c3.direction());
        assertEquals(PaymentMethod.CASH, c3.inferredPaymentMethod());
        assertEquals("TXN998877", c3.externalReference());
        assertEquals(new BigDecimal("132300.50"), c3.balanceAfter());
    }

    @Test
    void extract_singleAmountColumnWithDrCrType() {
        String csv = """
            Txn Date,Particulars,Reference No,Amount,Txn Type,Balance
            15-Aug-2026,POS 4532 ZOMATO RESTAURANT,RRN887766,780.25,DR,24500.00
            18-Aug-2026,DIVIDEND CREDITED INFY,DIV112233,1250.00,CR,25750.00
            """;

        ByteArrayInputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        List<TransactionCandidate> candidates = extractor.extract(documentId, DocumentAccountType.SAVINGS, stream);

        assertEquals(2, candidates.size());

        TransactionCandidate c1 = candidates.get(0);
        assertTrue(c1.isValid());
        assertEquals(LocalDate.of(2026, 8, 15), c1.transactionDate());
        assertEquals(new BigDecimal("780.25"), c1.amount());
        assertEquals(TransactionType.EXPENSE, c1.direction());
        assertEquals(PaymentMethod.DEBIT_CARD, c1.inferredPaymentMethod());
        assertEquals("RRN887766", c1.externalReference());

        TransactionCandidate c2 = candidates.get(1);
        assertTrue(c2.isValid());
        assertEquals(LocalDate.of(2026, 8, 18), c2.transactionDate());
        assertEquals(new BigDecimal("1250.00"), c2.amount());
        assertEquals(TransactionType.INCOME, c2.direction());
        assertEquals(PaymentMethod.OTHER, c2.inferredPaymentMethod());
    }

    @Test
    void extract_creditCardStatement() {
        String csv = """
            Transaction Date,Description,Amount,Type
            2026-08-10,AMAZON INDIA RETAIL,1499.00,PURCHASE
            2026-08-12,CRED AUTOPAY PAYMENT,25000.00,PAYMENT
            2026-08-14,FLIPKART REFUND REVERSAL,499.00,REFUND
            """;

        ByteArrayInputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        List<TransactionCandidate> candidates = extractor.extract(documentId, DocumentAccountType.CREDIT_CARD, stream);

        assertEquals(3, candidates.size());

        TransactionCandidate c1 = candidates.get(0);
        assertTrue(c1.isValid());
        assertEquals(SourceTransactionClassification.PURCHASE, c1.classification());
        assertEquals(TransactionType.EXPENSE, c1.direction());
        assertEquals(new BigDecimal("1499.00"), c1.amount());
        assertEquals(PaymentMethod.CREDIT_CARD, c1.inferredPaymentMethod());
        assertEquals("PURCHASE", c1.rawTypeIndicator());

        TransactionCandidate c2 = candidates.get(1);
        assertTrue(c2.isValid());
        assertEquals(SourceTransactionClassification.PAYMENT, c2.classification());
        assertNull(c2.direction(), "Card payment must NOT be automatically classified as INCOME");
        assertEquals(new BigDecimal("25000.00"), c2.amount());
        assertEquals("PAYMENT", c2.rawTypeIndicator());

        TransactionCandidate c3 = candidates.get(2);
        assertTrue(c3.isValid());
        assertEquals(SourceTransactionClassification.REFUND, c3.classification());
        assertNull(c3.direction(), "Card refund must NOT be automatically classified as ordinary INCOME");
        assertEquals(new BigDecimal("499.00"), c3.amount());
        assertEquals("REFUND", c3.rawTypeIndicator());
    }

    @Test
    void extract_bankStatementWithCcPaymentAndSalaryAndRefund() {
        String csv = """
            Date,Narration,Withdrawal,Deposit
            2026-08-01,SALARY CREDIT FROM ACME CORP,,85000.00
            2026-08-02,PAYMENT TO HDFC CREDIT CARD,20000.00,
            2026-08-03,UPI/423456789012/SWIGGY BANGALORE,642.00,
            2026-08-04,AMAZON REFUND RECEIVED,,1200.00
            """;

        ByteArrayInputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        List<TransactionCandidate> candidates = extractor.extract(documentId, DocumentAccountType.SAVINGS, stream);

        assertEquals(4, candidates.size());

        // 1. Salary deposit -> DEPOSIT, INCOME
        TransactionCandidate c1 = candidates.get(0);
        assertTrue(c1.isValid());
        assertEquals(SourceTransactionClassification.DEPOSIT, c1.classification());
        assertEquals(TransactionType.INCOME, c1.direction());
        assertEquals(new BigDecimal("85000.00"), c1.amount());

        // 2. Bank payment to CC -> PAYMENT, direction null (NOT ordinary EXPENSE)
        TransactionCandidate c2 = candidates.get(1);
        assertTrue(c2.isValid());
        assertEquals(SourceTransactionClassification.PAYMENT, c2.classification());
        assertNull(c2.direction(), "Bank payment to credit card must not be ordinary expense");
        assertEquals(new BigDecimal("20000.00"), c2.amount());

        // 3. UPI Swiggy -> PURCHASE, EXPENSE
        TransactionCandidate c3 = candidates.get(2);
        assertTrue(c3.isValid());
        assertEquals(SourceTransactionClassification.PURCHASE, c3.classification());
        assertEquals(TransactionType.EXPENSE, c3.direction());
        assertEquals(new BigDecimal("642.00"), c3.amount());

        // 4. Amazon refund -> REFUND, direction null (NOT ordinary INCOME)
        TransactionCandidate c4 = candidates.get(3);
        assertTrue(c4.isValid());
        assertEquals(SourceTransactionClassification.REFUND, c4.classification());
        assertNull(c4.direction(), "Refund must not be ordinary income");
        assertEquals(new BigDecimal("1200.00"), c4.amount());
    }

    @Test
    void extract_invalidRowsProduceStructuredErrors() {
        String csv = """
            Date,Description,Withdrawal,Deposit
            2026-08-01,Valid Transaction,100.00,
            invalid-date-string,Invalid Date Row,200.00,
            2026-08-03,Zero Amount Row,0.00,
            """;

        ByteArrayInputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        List<TransactionCandidate> candidates = extractor.extract(documentId, DocumentAccountType.SAVINGS, stream);

        assertEquals(3, candidates.size());

        assertTrue(candidates.get(0).isValid());

        TransactionCandidate badDate = candidates.get(1);
        assertFalse(badDate.isValid());
        assertTrue(badDate.validationResult().getErrors().stream()
            .anyMatch(e -> e.code().equals(CandidateValidationError.CODE_INVALID_DATE)));

        TransactionCandidate zeroAmt = candidates.get(2);
        assertFalse(zeroAmt.isValid());
        assertTrue(zeroAmt.validationResult().getErrors().stream()
            .anyMatch(e -> e.code().equals(CandidateValidationError.CODE_MISSING_AMOUNT)
                || e.code().equals(CandidateValidationError.CODE_INVALID_AMOUNT)));
    }

    @Test
    void extract_noHeaderFound_throwsException() {
        String csv = """
            Just some text
            Without any table headers
            Or monetary columns
            """;

        ByteArrayInputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        DocumentExtractionException ex = assertThrows(DocumentExtractionException.class, () ->
            extractor.extract(documentId, DocumentAccountType.SAVINGS, stream));

        assertEquals(DocumentExtractionException.ErrorCode.NO_HEADER_FOUND, ex.getErrorCode());
    }

    @Test
    void extract_emptyDocument_throwsException() {
        ByteArrayInputStream stream = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        DocumentExtractionException ex = assertThrows(DocumentExtractionException.class, () ->
            extractor.extract(documentId, DocumentAccountType.SAVINGS, stream));

        assertEquals(DocumentExtractionException.ErrorCode.EMPTY_DOCUMENT, ex.getErrorCode());
    }
}
