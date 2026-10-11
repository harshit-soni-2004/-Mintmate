package com.spendsense.ingestion.extractor;

import com.spendsense.document.DocumentAccountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Bank Institution & Account Metadata Detector Tests")
class BankInstitutionDetectorTest {

    @Test
    @DisplayName("Should detect State Bank of India and last-4 from SBI header lines")
    void shouldDetectSbiAndLast4() {
        List<String> sbiLines = List.of(
                "STATEMENT OF ACCOUNT",
                "Mr. HARSHIT ASHOK SONI State Bank of India",
                "Account Number : 30582558821",
                "Product : REGULAR SB CHQ-INDIVIDUALS",
                "IFSC Code : SBIN0003117"
        );

        StatementDocumentMetadata metadata = BankInstitutionDetector.detectMetadata(sbiLines);
        assertEquals("State Bank of India", metadata.institutionName());
        assertEquals("8821", metadata.accountNumberLast4());
        assertEquals(DocumentAccountType.SAVINGS, metadata.accountType());
    }

    @Test
    @DisplayName("Should detect Kotak Mahindra Bank and last-4 from Kotak header lines")
    void shouldDetectKotakAndLast4() {
        List<String> kotakLines = List.of(
                "Account Statement",
                "Harshit Ashok Soni",
                "Account No. 4056291795",
                "Account Type Savings",
                "MICR 400485064 IFSC Code KKBK0001351"
        );

        StatementDocumentMetadata metadata = BankInstitutionDetector.detectMetadata(kotakLines);
        assertEquals("Kotak Mahindra Bank", metadata.institutionName());
        assertEquals("1795", metadata.accountNumberLast4());
        assertEquals(DocumentAccountType.SAVINGS, metadata.accountType());
    }

    @Test
    @DisplayName("Should detect masked account numbers (XXXXXXXX1234)")
    void shouldDetectMaskedAccountNumber() {
        List<String> lines = List.of(
                "HDFC BANK STATEMENT",
                "A/c No: XXXXXXXX5678",
                "IFSC: HDFC0000123"
        );

        StatementDocumentMetadata metadata = BankInstitutionDetector.detectMetadata(lines);
        assertEquals("HDFC Bank", metadata.institutionName());
        assertEquals("5678", metadata.accountNumberLast4());
    }

    @Test
    @DisplayName("Should detect metadata directly from real SBI statement PDF")
    void shouldDetectFromRealSbiPdf() throws Exception {
        File file = new File("../AccountStatement_20092026_25810.pdf");
        if (!file.exists()) {
            file = new File("AccountStatement_20092026_25810.pdf");
        }
        if (!file.exists()) {
            return;
        }

        PdfStatementExtractor extractor = new PdfStatementExtractor(null, null);
        try (InputStream is = new FileInputStream(file)) {
            StatementDocumentMetadata meta = extractor.extractMetadata(is);
            assertEquals("State Bank of India", meta.institutionName());
            assertEquals("8821", meta.accountNumberLast4());
            assertEquals(DocumentAccountType.SAVINGS, meta.accountType());
        }
    }

    @Test
    @DisplayName("Should detect metadata directly from real Kotak statement PDF")
    void shouldDetectFromRealKotakPdf() throws Exception {
        File file = new File("../AccountStatement_01-Jun-2026_31-Aug-2026.pdf");
        if (!file.exists()) {
            file = new File("AccountStatement_01-Jun-2026_31-Aug-2026.pdf");
        }
        if (!file.exists()) {
            return;
        }

        PdfStatementExtractor extractor = new PdfStatementExtractor(null, null);
        try (InputStream is = new FileInputStream(file)) {
            StatementDocumentMetadata meta = extractor.extractMetadata(is);
            assertEquals("Kotak Mahindra Bank", meta.institutionName());
            assertEquals("1795", meta.accountNumberLast4());
            assertEquals(DocumentAccountType.SAVINGS, meta.accountType());
        }
    }
}
