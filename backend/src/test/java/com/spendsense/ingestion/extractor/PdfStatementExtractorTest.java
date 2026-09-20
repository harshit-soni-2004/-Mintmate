package com.spendsense.ingestion.extractor;

import com.spendsense.document.DocumentAccountType;
import com.spendsense.document.DocumentFileType;
import com.spendsense.ingestion.CandidateValidator;
import com.spendsense.ingestion.SourceTransactionClassification;
import com.spendsense.ingestion.StatementNormalizer;
import com.spendsense.ingestion.TransactionCandidate;
import com.spendsense.transaction.PaymentMethod;
import com.spendsense.transaction.TransactionType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
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

class PdfStatementExtractorTest {

    private PdfStatementExtractor extractor;
    private final UUID documentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        StatementNormalizer normalizer = new StatementNormalizer();
        CandidateValidator validator = new CandidateValidator();
        extractor = new PdfStatementExtractor(normalizer, validator);
    }

    @Test
    void supports_onlyPdf() {
        assertTrue(extractor.supports(DocumentFileType.PDF));
        assertFalse(extractor.supports(DocumentFileType.CSV));
        assertFalse(extractor.supports(DocumentFileType.XLSX));
    }

    @Test
    void extract_singlePageBankStatement_withdrawalDepositBalance() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // Header metadata
                writeText(cs, font, 50, 750, "HDFC BANK ACCOUNT STATEMENT");
                writeText(cs, font, 50, 735, "Account: 50100234567890  Branch: Indiranagar");

                // Table Header
                writeText(cs, font, 50, 700, "Date");
                writeText(cs, font, 120, 700, "Narration");
                writeText(cs, font, 280, 700, "Chq/Ref");
                writeText(cs, font, 360, 700, "Withdrawal");
                writeText(cs, font, 440, 700, "Deposit");
                writeText(cs, font, 510, 700, "Balance");

                // Row 1: UPI Swiggy Purchase
                writeText(cs, font, 50, 680, "01/08/2026");
                writeText(cs, font, 120, 680, "UPI SWIGGY BANGALORE");
                writeText(cs, font, 280, 680, "423456789012");
                writeText(cs, font, 360, 680, "450.00");
                writeText(cs, font, 510, 680, "52300.50");

                // Row 2: Salary Deposit
                writeText(cs, font, 50, 660, "05/08/2026");
                writeText(cs, font, 120, 660, "NEFT TECH CORP SALARY");
                writeText(cs, font, 280, 660, "N12345678");
                writeText(cs, font, 440, 660, "85000.00");
                writeText(cs, font, 510, 660, "137300.50");

                // Row 3: Bank CC Bill Payment (PAYMENT -> direction null)
                writeText(cs, font, 50, 640, "12/08/2026");
                writeText(cs, font, 120, 640, "PAYMENT TO HDFC CC");
                writeText(cs, font, 280, 640, "CC998877");
                writeText(cs, font, 360, 640, "20000.00");
                writeText(cs, font, 510, 640, "117300.50");

                // Row 4: Refund received (REFUND -> direction null)
                writeText(cs, font, 50, 620, "15/08/2026");
                writeText(cs, font, 120, 620, "AMAZON REFUND");
                writeText(cs, font, 280, 620, "REF112233");
                writeText(cs, font, 440, 620, "2000.00");
                writeText(cs, font, 510, 620, "119300.50");

                // Summary / Footer rows
                writeText(cs, font, 50, 580, "Total Withdrawals: 20450.00  Total Deposits: 87000.00");
                writeText(cs, font, 50, 565, "*** End of Statement ***");
                writeText(cs, font, 250, 40, "Page 1 of 1");
            }
            doc.save(bos);
        }

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        List<TransactionCandidate> candidates = extractor.extract(documentId, DocumentAccountType.SAVINGS, bis);

        assertEquals(4, candidates.size());

        // 1. UPI Swiggy
        TransactionCandidate c1 = candidates.get(0);
        assertTrue(c1.isValid());
        assertEquals(LocalDate.of(2026, 8, 1), c1.transactionDate());
        assertEquals(new BigDecimal("450.00"), c1.amount());
        assertEquals(SourceTransactionClassification.PURCHASE, c1.classification());
        assertEquals(TransactionType.EXPENSE, c1.direction());
        assertEquals(PaymentMethod.UPI, c1.inferredPaymentMethod());
        assertEquals("423456789012", c1.externalReference());
        assertEquals(new BigDecimal("52300.50"), c1.balanceAfter());

        // 2. Salary Deposit
        TransactionCandidate c2 = candidates.get(1);
        assertTrue(c2.isValid());
        assertEquals(LocalDate.of(2026, 8, 5), c2.transactionDate());
        assertEquals(new BigDecimal("85000.00"), c2.amount());
        assertEquals(SourceTransactionClassification.DEPOSIT, c2.classification());
        assertEquals(TransactionType.INCOME, c2.direction());
        assertEquals(PaymentMethod.BANK_TRANSFER, c2.inferredPaymentMethod());
        assertEquals("N12345678", c2.externalReference());
        assertEquals(new BigDecimal("137300.50"), c2.balanceAfter());

        // 3. CC Bill Payment -> PAYMENT, direction null (NOT ordinary EXPENSE)
        TransactionCandidate c3 = candidates.get(2);
        assertTrue(c3.isValid());
        assertEquals(new BigDecimal("20000.00"), c3.amount());
        assertEquals(SourceTransactionClassification.PAYMENT, c3.classification());
        assertNull(c3.direction(), "Bank payment to credit card must not be ordinary expense");
        assertEquals("CC998877", c3.externalReference());

        // 4. Amazon refund -> REFUND, direction null (NOT ordinary INCOME)
        TransactionCandidate c4 = candidates.get(3);
        assertTrue(c4.isValid());
        assertEquals(new BigDecimal("2000.00"), c4.amount());
        assertEquals(SourceTransactionClassification.REFUND, c4.classification());
        assertNull(c4.direction(), "Refund must not be ordinary income");
        assertEquals("REF112233", c4.externalReference());
    }

    @Test
    void extract_creditCardStatement_purchasePaymentRefund() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                writeText(cs, font, 50, 750, "CREDIT CARD STATEMENT");

                // Header
                writeText(cs, font, 50, 700, "Date");
                writeText(cs, font, 130, 700, "Transaction Details");
                writeText(cs, font, 380, 700, "Amount");
                writeText(cs, font, 460, 700, "Type");

                // Purchase
                writeText(cs, font, 50, 680, "10-Aug-2026");
                writeText(cs, font, 130, 680, "SWIGGY BANGALORE");
                writeText(cs, font, 380, 680, "642.00");
                writeText(cs, font, 460, 680, "PURCHASE");

                // Card payment received
                writeText(cs, font, 50, 660, "12-Aug-2026");
                writeText(cs, font, 130, 660, "PAYMENT RECEIVED VIA IMPS");
                writeText(cs, font, 380, 660, "20000.00");
                writeText(cs, font, 460, 660, "PAYMENT");

                // Refund
                writeText(cs, font, 50, 640, "14-Aug-2026");
                writeText(cs, font, 130, 640, "AMAZON REFUND");
                writeText(cs, font, 380, 640, "2000.00");
                writeText(cs, font, 460, 640, "REFUND");
            }
            doc.save(bos);
        }

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        List<TransactionCandidate> candidates = extractor.extract(documentId, DocumentAccountType.CREDIT_CARD, bis);

        assertEquals(3, candidates.size());

        // 1. Purchase -> PURCHASE, EXPENSE
        TransactionCandidate c1 = candidates.get(0);
        assertTrue(c1.isValid());
        assertEquals(SourceTransactionClassification.PURCHASE, c1.classification());
        assertEquals(TransactionType.EXPENSE, c1.direction());
        assertEquals(new BigDecimal("642.00"), c1.amount());
        assertEquals(PaymentMethod.CREDIT_CARD, c1.inferredPaymentMethod());

        // 2. Card Payment -> PAYMENT, direction null (NOT INCOME)
        TransactionCandidate c2 = candidates.get(1);
        assertTrue(c2.isValid());
        assertEquals(SourceTransactionClassification.PAYMENT, c2.classification());
        assertNull(c2.direction(), "Credit card payment must NOT be classified as INCOME");
        assertEquals(new BigDecimal("20000.00"), c2.amount());

        // 3. Card Refund -> REFUND, direction null (NOT ordinary INCOME)
        TransactionCandidate c3 = candidates.get(2);
        assertTrue(c3.isValid());
        assertEquals(SourceTransactionClassification.REFUND, c3.classification());
        assertNull(c3.direction(), "Credit card refund must NOT be ordinary INCOME");
        assertEquals(new BigDecimal("2000.00"), c3.amount());
    }

    @Test
    void extract_multiPageStatementWithRepeatedHeadersAndPageFooters() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            // Page 1
            PDPage page1 = new PDPage(PDRectangle.A4);
            doc.addPage(page1);
            try (PDPageContentStream cs1 = new PDPageContentStream(doc, page1)) {
                writeText(cs1, font, 50, 750, "BANK STATEMENT - PAGE 1");
                writeText(cs1, font, 50, 700, "Date");
                writeText(cs1, font, 120, 700, "Narration");
                writeText(cs1, font, 360, 700, "Withdrawal");
                writeText(cs1, font, 440, 700, "Deposit");

                writeText(cs1, font, 50, 680, "01/08/2026");
                writeText(cs1, font, 120, 680, "POS 4532 ZOMATO RESTAURANT");
                writeText(cs1, font, 360, 680, "520.00");

                writeText(cs1, font, 50, 660, "02/08/2026");
                writeText(cs1, font, 120, 660, "UPI/123/BLINKIT GROCERY");
                writeText(cs1, font, 360, 660, "315.00");

                writeText(cs1, font, 250, 40, "Page 1 of 2");
            }

            // Page 2
            PDPage page2 = new PDPage(PDRectangle.A4);
            doc.addPage(page2);
            try (PDPageContentStream cs2 = new PDPageContentStream(doc, page2)) {
                writeText(cs2, font, 50, 750, "BANK STATEMENT - PAGE 2");
                // Repeated header on page 2
                writeText(cs2, font, 50, 700, "Date");
                writeText(cs2, font, 120, 700, "Narration");
                writeText(cs2, font, 360, 700, "Withdrawal");
                writeText(cs2, font, 440, 700, "Deposit");

                writeText(cs2, font, 50, 680, "05/08/2026");
                writeText(cs2, font, 120, 680, "SALARY CREDIT TECH CORP");
                writeText(cs2, font, 440, 680, "85000.00");

                writeText(cs2, font, 50, 660, "10/08/2026");
                writeText(cs2, font, 120, 660, "ATM WDL CASH 5000 BANGALORE");
                writeText(cs2, font, 360, 660, "5000.00");

                writeText(cs2, font, 50, 600, "Total Withdrawals: 5835.00 Total Deposits: 85000.00");
                writeText(cs2, font, 250, 40, "Page 2 of 2");
            }

            doc.save(bos);
        }

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        List<TransactionCandidate> candidates = extractor.extract(documentId, DocumentAccountType.SAVINGS, bis);

        assertEquals(4, candidates.size());
        assertEquals(new BigDecimal("520.00"), candidates.get(0).amount());
        assertEquals(new BigDecimal("315.00"), candidates.get(1).amount());
        assertEquals(new BigDecimal("85000.00"), candidates.get(2).amount());
        assertEquals(new BigDecimal("5000.00"), candidates.get(3).amount());
    }

    @Test
    void extract_wrappedNarrationLine_appendsToPreviousTransaction() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                writeText(cs, font, 50, 700, "Date");
                writeText(cs, font, 120, 700, "Narration");
                writeText(cs, font, 360, 700, "Withdrawal");
                writeText(cs, font, 440, 700, "Deposit");

                // Main transaction line
                writeText(cs, font, 50, 680, "01/08/2026");
                writeText(cs, font, 120, 680, "UPI/423456789012/SWIGGY BANGALORE/");
                writeText(cs, font, 360, 680, "450.00");

                // Wrapped description line without date or amounts
                writeText(cs, font, 120, 665, "swiggy@icici Order #998877 Delivery Partner Tip");

                // Next transaction line
                writeText(cs, font, 50, 645, "02/08/2026");
                writeText(cs, font, 120, 645, "UBER TRIP BANGALORE");
                writeText(cs, font, 360, 645, "250.00");
            }
            doc.save(bos);
        }

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        List<TransactionCandidate> candidates = extractor.extract(documentId, DocumentAccountType.SAVINGS, bis);

        assertEquals(2, candidates.size());

        TransactionCandidate c1 = candidates.get(0);
        assertTrue(c1.rawDescription().contains("SWIGGY BANGALORE/"));
        assertTrue(c1.rawDescription().contains("swiggy@icici Order #998877"));
        assertEquals(PaymentMethod.UPI, c1.inferredPaymentMethod());
        assertEquals("423456789012", c1.externalReference());
    }

    @Test
    void extract_passwordProtectedPdf_throwsException() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                writeText(cs, font, 50, 700, "Secret content");
            }

            AccessPermission ap = new AccessPermission();
            StandardProtectionPolicy spp = new StandardProtectionPolicy("owner123", "user123", ap);
            doc.protect(spp);
            doc.save(bos);
        }

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        DocumentExtractionException ex = assertThrows(DocumentExtractionException.class, () ->
            extractor.extract(documentId, DocumentAccountType.SAVINGS, bis));

        assertEquals(DocumentExtractionException.ErrorCode.PASSWORD_PROTECTED_DOCUMENT, ex.getErrorCode());
    }

    @Test
    void extract_scannedOrImageOnlyPdf_throwsException() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            // Empty page with no text content
            doc.save(bos);
        }

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        DocumentExtractionException ex = assertThrows(DocumentExtractionException.class, () ->
            extractor.extract(documentId, DocumentAccountType.SAVINGS, bis));

        assertEquals(DocumentExtractionException.ErrorCode.SCANNED_DOCUMENT_UNSUPPORTED, ex.getErrorCode());
    }

    @Test
    void extract_corruptedPdf_throwsException() {
        ByteArrayInputStream bis = new ByteArrayInputStream("THIS IS NOT A VALID PDF CONTENT".getBytes());
        DocumentExtractionException ex = assertThrows(DocumentExtractionException.class, () ->
            extractor.extract(documentId, DocumentAccountType.SAVINGS, bis));

        assertEquals(DocumentExtractionException.ErrorCode.CORRUPTED_FILE, ex.getErrorCode());
    }

    @Test
    void extract_emptyPdfStream_throwsException() {
        ByteArrayInputStream bis = new ByteArrayInputStream(new byte[0]);
        DocumentExtractionException ex = assertThrows(DocumentExtractionException.class, () ->
            extractor.extract(documentId, DocumentAccountType.SAVINGS, bis));

        assertEquals(DocumentExtractionException.ErrorCode.EMPTY_DOCUMENT, ex.getErrorCode());
    }

    @Test
    void extract_pdfWithNoRecognizableTransactionHeader_throwsException() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                writeText(cs, font, 50, 700, "Terms and conditions of account usage");
                writeText(cs, font, 50, 680, "No financial tables or transaction records exist here.");
            }
            doc.save(bos);
        }

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        DocumentExtractionException ex = assertThrows(DocumentExtractionException.class, () ->
            extractor.extract(documentId, DocumentAccountType.SAVINGS, bis));

        assertEquals(DocumentExtractionException.ErrorCode.NO_HEADER_FOUND, ex.getErrorCode());
    }

    @Test
    void extract_headerlessTextLayerStatement_infersLayoutAndExtractsDebitAndCreditCandidates() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            // Page 1
            PDPage page1 = new PDPage(PDRectangle.A4);
            doc.addPage(page1);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page1)) {
                // Lone "Balance" header artifact without column headers
                writeText(cs, font, 514, 750, "Balance");

                // Transaction 1: Debit with prefix line
                writeText(cs, font, 138, 717, "WDL TFR");
                writeText(cs, font, 27, 710, "01/04/2026");
                writeText(cs, font, 82, 710, "01/04/2026");
                writeText(cs, font, 138, 710, "UPI/DR/609162490365/RAJAN");
                writeText(cs, font, 303, 710, "-");
                writeText(cs, font, 357, 710, "15.00");
                writeText(cs, font, 446, 710, "-");
                writeText(cs, font, 514, 710, "1,214.47");
                // Narration continuations
                writeText(cs, font, 138, 698, "KU/YESB/paytmqr6si/Sent");
                writeText(cs, font, 138, 689, "0097692162094 AT 03117");
                writeText(cs, font, 138, 680, "VERSOVA (MUMBAI)");

                // Transaction 2: Debit with prefix line
                writeText(cs, font, 138, 647, "WDL TFR");
                writeText(cs, font, 27, 640, "01/04/2026");
                writeText(cs, font, 82, 640, "01/04/2026");
                writeText(cs, font, 138, 640, "UPI/DR/601955483548/RANCHHO");
                writeText(cs, font, 303, 640, "-");
                writeText(cs, font, 357, 640, "12.00");
                writeText(cs, font, 446, 640, "-");
                writeText(cs, font, 514, 640, "1,202.47");
                writeText(cs, font, 138, 628, "D/YESB/q976271806/Sent");

                // Page footer
                writeText(cs, font, 269, 50, "Page no. 1");
            }

            // Page 2
            PDPage page2 = new PDPage(PDRectangle.A4);
            doc.addPage(page2);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page2)) {
                // Repeated "Balance" header artifact
                writeText(cs, font, 514, 750, "Balance");

                // Transaction 3: NEFT Credit with prefix line
                writeText(cs, font, 138, 717, "DEP TFR");
                writeText(cs, font, 27, 710, "02/04/2026");
                writeText(cs, font, 82, 710, "02/04/2026");
                writeText(cs, font, 138, 710, "NEFT*IDFB0010201*IDFBH26092");
                writeText(cs, font, 303, 710, "-");
                writeText(cs, font, 366, 710, "-");
                writeText(cs, font, 430, 710, "11,800.00");
                writeText(cs, font, 512, 710, "12,387.47");
                writeText(cs, font, 138, 698, "966758*RZPX PVT LTD PA");

                // Page footer
                writeText(cs, font, 269, 50, "Page no. 2");
            }

            doc.save(bos);
        }

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        List<TransactionCandidate> candidates = extractor.extract(documentId, DocumentAccountType.SAVINGS, bis);

        assertEquals(3, candidates.size());

        // Candidate 1: Debit
        TransactionCandidate c1 = candidates.get(0);
        assertEquals(LocalDate.of(2026, 4, 1), c1.transactionDate());
        assertEquals(new BigDecimal("15.00"), c1.amount());
        assertEquals(TransactionType.EXPENSE, c1.direction());
        assertEquals(new BigDecimal("1214.47"), c1.balanceAfter());
        assertTrue(c1.rawDescription().contains("WDL TFR"));
        assertTrue(c1.rawDescription().contains("UPI/DR/609162490365/RAJAN"));
        assertTrue(c1.rawDescription().contains("KU/YESB/paytmqr6si/Sent"));
        assertTrue(c1.rawDescription().contains("VERSOVA (MUMBAI)"));
        assertTrue(c1.validationResult().isValid());

        // Candidate 2: Debit
        TransactionCandidate c2 = candidates.get(1);
        assertEquals(LocalDate.of(2026, 4, 1), c2.transactionDate());
        assertEquals(new BigDecimal("12.00"), c2.amount());
        assertEquals(TransactionType.EXPENSE, c2.direction());
        assertEquals(new BigDecimal("1202.47"), c2.balanceAfter());
        assertTrue(c2.rawDescription().contains("UPI/DR/601955483548/RANCHHO"));
        assertTrue(c2.validationResult().isValid());

        // Candidate 3: Credit
        TransactionCandidate c3 = candidates.get(2);
        assertEquals(LocalDate.of(2026, 4, 2), c3.transactionDate());
        assertEquals(new BigDecimal("11800.00"), c3.amount());
        assertEquals(TransactionType.INCOME, c3.direction());
        assertEquals(new BigDecimal("12387.47"), c3.balanceAfter());
        assertTrue(c3.rawDescription().contains("DEP TFR"));
        assertTrue(c3.rawDescription().contains("NEFT*IDFB0010201*IDFBH26092"));
        assertTrue(c3.rawDescription().contains("966758*RZPX PVT LTD PA"));
        assertTrue(c3.validationResult().isValid());
    }

    private static void writeText(PDPageContentStream cs, PDType1Font font, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, 10);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }
}
