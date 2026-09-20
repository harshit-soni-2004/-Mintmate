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
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExcelStatementExtractorTest {

    private ExcelStatementExtractor extractor;
    private final UUID documentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        StatementNormalizer normalizer = new StatementNormalizer();
        CandidateValidator validator = new CandidateValidator();
        extractor = new ExcelStatementExtractor(normalizer, validator);
    }

    @Test
    void supports_onlyXlsx() {
        assertTrue(extractor.supports(DocumentFileType.XLSX));
        assertFalse(extractor.supports(DocumentFileType.CSV));
        assertFalse(extractor.supports(DocumentFileType.PDF));
    }

    @Test
    void extract_multiSheetWorkbook_detectsTransactionSheetAndNativeDates() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            // Sheet 1: Summary sheet (no transactions)
            Sheet summarySheet = wb.createSheet("Summary");
            Row sRow0 = summarySheet.createRow(0);
            sRow0.createCell(0).setCellValue("Account Summary");
            Row sRow1 = summarySheet.createRow(1);
            sRow1.createCell(0).setCellValue("Account Number");
            sRow1.createCell(1).setCellValue("XXXX-XXXX-1234");

            // Sheet 2: Statement transactions
            Sheet txnSheet = wb.createSheet("Statement");
            CellStyle dateStyle = wb.createCellStyle();
            CreationHelper helper = wb.getCreationHelper();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));

            // Header row at index 0
            Row hRow = txnSheet.createRow(0);
            hRow.createCell(0).setCellValue("Date");
            hRow.createCell(1).setCellValue("Narration");
            hRow.createCell(2).setCellValue("Chq / Ref No");
            hRow.createCell(3).setCellValue("Withdrawal (Dr)");
            hRow.createCell(4).setCellValue("Deposit (Cr)");
            hRow.createCell(5).setCellValue("Balance");

            // Row 1: Swiggy UPI expense
            Row row1 = txnSheet.createRow(1);
            Cell d1 = row1.createCell(0);
            d1.setCellValue(LocalDate.of(2026, 8, 1));
            d1.setCellStyle(dateStyle);
            row1.createCell(1).setCellValue("UPI/423456789012/Swiggy Bangalore/swiggy@icici");
            row1.createCell(2).setCellValue("423456789012");
            row1.createCell(3).setCellValue(450.00);
            row1.createCell(5).setCellValue(52300.50);

            // Row 2: Salary NEFT income
            Row row2 = txnSheet.createRow(2);
            Cell d2 = row2.createCell(0);
            d2.setCellValue(LocalDate.of(2026, 8, 5));
            d2.setCellStyle(dateStyle);
            row2.createCell(1).setCellValue("NEFT CR-N12345678-TECH CORP SALARY");
            row2.createCell(2).setCellValue("N12345678");
            row2.createCell(4).setCellValue(85000.00);
            row2.createCell(5).setCellValue(137300.50);

            // Row 3: Summary row
            Row row3 = txnSheet.createRow(3);
            row3.createCell(0).setCellValue("Total");
            row3.createCell(3).setCellValue(450.00);
            row3.createCell(4).setCellValue(85000.00);

            wb.write(bos);
        }

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        List<TransactionCandidate> candidates = extractor.extract(documentId, DocumentAccountType.SAVINGS, bis);

        assertEquals(2, candidates.size());

        TransactionCandidate c1 = candidates.get(0);
        assertTrue(c1.isValid());
        assertEquals(LocalDate.of(2026, 8, 1), c1.transactionDate());
        assertEquals(new BigDecimal("450.0"), c1.amount());
        assertEquals(SourceTransactionClassification.PURCHASE, c1.classification());
        assertEquals(TransactionType.EXPENSE, c1.direction());
        assertEquals(PaymentMethod.UPI, c1.inferredPaymentMethod());
        assertEquals("423456789012", c1.externalReference());

        TransactionCandidate c2 = candidates.get(1);
        assertTrue(c2.isValid());
        assertEquals(LocalDate.of(2026, 8, 5), c2.transactionDate());
        assertEquals(new BigDecimal("85000.0"), c2.amount());
        assertEquals(SourceTransactionClassification.DEPOSIT, c2.classification());
        assertEquals(TransactionType.INCOME, c2.direction());
        assertEquals(PaymentMethod.BANK_TRANSFER, c2.inferredPaymentMethod());
        assertEquals("N12345678", c2.externalReference());
    }

    @Test
    void extract_creditCardStatement_preservesSemanticsWithoutPrematureIncome() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("CardStatement");

            Row hRow = sheet.createRow(0);
            hRow.createCell(0).setCellValue("Date");
            hRow.createCell(1).setCellValue("Transaction Details");
            hRow.createCell(2).setCellValue("Amount");
            hRow.createCell(3).setCellValue("Type");

            // Row 1: Purchase
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("2026-08-10");
            r1.createCell(1).setCellValue("SWIGGY BANGALORE");
            r1.createCell(2).setCellValue(642.00);
            r1.createCell(3).setCellValue("PURCHASE");

            // Row 2: Payment Received
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("2026-08-12");
            r2.createCell(1).setCellValue("PAYMENT RECEIVED VIA IMPS");
            r2.createCell(2).setCellValue(20000.00);
            r2.createCell(3).setCellValue("PAYMENT");

            // Row 3: Refund
            Row r3 = sheet.createRow(3);
            r3.createCell(0).setCellValue("2026-08-14");
            r3.createCell(1).setCellValue("AMAZON REFUND");
            r3.createCell(2).setCellValue(2000.00);
            r3.createCell(3).setCellValue("REFUND");

            wb.write(bos);
        }

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        List<TransactionCandidate> candidates = extractor.extract(documentId, DocumentAccountType.CREDIT_CARD, bis);

        assertEquals(3, candidates.size());

        // 1. Purchase -> PURCHASE, EXPENSE
        TransactionCandidate c1 = candidates.get(0);
        assertTrue(c1.isValid());
        assertEquals(SourceTransactionClassification.PURCHASE, c1.classification());
        assertEquals(TransactionType.EXPENSE, c1.direction());
        assertEquals(new BigDecimal("642.0"), c1.amount());
        assertEquals(PaymentMethod.CREDIT_CARD, c1.inferredPaymentMethod());

        // 2. Card Payment -> PAYMENT, direction null (NOT INCOME)
        TransactionCandidate c2 = candidates.get(1);
        assertTrue(c2.isValid());
        assertEquals(SourceTransactionClassification.PAYMENT, c2.classification());
        assertNull(c2.direction(), "Credit-card payment must NOT be automatically classified as INCOME");
        assertEquals(new BigDecimal("20000.0"), c2.amount());

        // 3. Card Refund -> REFUND, direction null (NOT ordinary INCOME)
        TransactionCandidate c3 = candidates.get(2);
        assertTrue(c3.isValid());
        assertEquals(SourceTransactionClassification.REFUND, c3.classification());
        assertNull(c3.direction(), "Credit-card refund must NOT be automatically classified as ordinary INCOME");
        assertEquals(new BigDecimal("2000.0"), c3.amount());
    }

    @Test
    void extract_titleRowsBeforeHeaderAndStringDates() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Transactions");

            // Row 0-2: Metadata
            sheet.createRow(0).createCell(0).setCellValue("HDFC Bank Account Statement");
            sheet.createRow(1).createCell(0).setCellValue("Customer: John Doe");
            sheet.createRow(2).createCell(0).setCellValue("Period: August 2026");

            // Row 3: Table Header
            Row hRow = sheet.createRow(3);
            hRow.createCell(0).setCellValue("Txn Date");
            hRow.createCell(1).setCellValue("Particulars");
            hRow.createCell(2).setCellValue("Amount");
            hRow.createCell(3).setCellValue("Type");

            // Row 4: Transaction 1
            Row r1 = sheet.createRow(4);
            r1.createCell(0).setCellValue("15-Aug-2026");
            r1.createCell(1).setCellValue("POS 4532 CAFE COFFEE DAY");
            r1.createCell(2).setCellValue(320.50);
            r1.createCell(3).setCellValue("DR");

            wb.write(bos);
        }

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        List<TransactionCandidate> candidates = extractor.extract(documentId, DocumentAccountType.SAVINGS, bis);

        assertEquals(1, candidates.size());
        TransactionCandidate c = candidates.get(0);
        assertTrue(c.isValid());
        assertEquals(LocalDate.of(2026, 8, 15), c.transactionDate());
        assertEquals(new BigDecimal("320.5"), c.amount());
        assertEquals(TransactionType.EXPENSE, c.direction());
        assertEquals(PaymentMethod.DEBIT_CARD, c.inferredPaymentMethod());
    }

    @Test
    void extract_invalidRowsProduceStructuredErrors() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet1");

            Row hRow = sheet.createRow(0);
            hRow.createCell(0).setCellValue("Date");
            hRow.createCell(1).setCellValue("Description");
            hRow.createCell(2).setCellValue("Amount");

            // Row 1: Valid
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("2026-08-01");
            r1.createCell(1).setCellValue("Valid transaction");
            r1.createCell(2).setCellValue(100.0);

            // Row 2: Bad date
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("not-a-date");
            r2.createCell(1).setCellValue("Invalid date transaction");
            r2.createCell(2).setCellValue(200.0);

            // Row 3: Zero amount
            Row r3 = sheet.createRow(3);
            r3.createCell(0).setCellValue("2026-08-03");
            r3.createCell(1).setCellValue("Zero amount transaction");
            r3.createCell(2).setCellValue(0.0);

            wb.write(bos);
        }

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        List<TransactionCandidate> candidates = extractor.extract(documentId, DocumentAccountType.SAVINGS, bis);

        assertEquals(3, candidates.size());
        assertTrue(candidates.get(0).isValid());
        assertFalse(candidates.get(1).isValid());
        assertTrue(candidates.get(1).validationResult().getErrors().stream()
            .anyMatch(e -> e.code().equals(CandidateValidationError.CODE_INVALID_DATE)));
        assertFalse(candidates.get(2).isValid());
        assertTrue(candidates.get(2).validationResult().getErrors().stream()
            .anyMatch(e -> e.code().equals(CandidateValidationError.CODE_INVALID_AMOUNT)));
    }

    @Test
    void extract_noHeaderFound_throwsException() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Blank");
            sheet.createRow(0).createCell(0).setCellValue("Only some random notes");
            wb.write(bos);
        }

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        DocumentExtractionException ex = assertThrows(DocumentExtractionException.class, () ->
            extractor.extract(documentId, DocumentAccountType.SAVINGS, bis));

        assertEquals(DocumentExtractionException.ErrorCode.NO_HEADER_FOUND, ex.getErrorCode());
    }
}
