package com.spendsense.document;

import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionSourceType;
import com.spendsense.transaction.TransactionType;
import com.spendsense.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Phase 7B: FinancialDocument & Transaction Source Domain Tests")
class FinancialDocumentTest {

    private final User user = new User("user@example.com", "hash", "Alice", "Asia/Kolkata");

    @Test
    @DisplayName("DocumentFileType parses extensions accurately and rejects unsupported formats")
    void fileTypeParsing() {
        assertThat(DocumentFileType.fromExtensionOrFilename("statement.PDF")).isEqualTo(DocumentFileType.PDF);
        assertThat(DocumentFileType.fromExtensionOrFilename("my_account.csv")).isEqualTo(DocumentFileType.CSV);
        assertThat(DocumentFileType.fromExtensionOrFilename("expenses.XLSX")).isEqualTo(DocumentFileType.XLSX);

        assertThatThrownBy(() -> DocumentFileType.fromExtensionOrFilename("file.exe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported file format");

        assertThatThrownBy(() -> DocumentFileType.fromExtensionOrFilename(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be empty");
    }

    @Test
    @DisplayName("FinancialDocument lifecycle status methods transition correctly")
    void lifecycleTransitions() {
        FinancialDocument doc = new FinancialDocument(
                user,
                "hdfc_statement.pdf",
                DocumentFileType.PDF,
                2048L,
                user.getId() + "/doc1.bin",
                "hash123",
                "HDFC Bank",
                DocumentAccountType.SAVINGS,
                "4321",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        );

        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(doc.getTotalRowsDetected()).isEqualTo(0);
        assertThat(doc.getImportedCount()).isEqualTo(0);
        assertThat(doc.getDuplicateCount()).isEqualTo(0);

        doc.markParsing();
        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.PARSING);

        doc.markCompleted(120, 115, 5);
        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(doc.getTotalRowsDetected()).isEqualTo(120);
        assertThat(doc.getImportedCount()).isEqualTo(115);
        assertThat(doc.getDuplicateCount()).isEqualTo(5);

        doc.markPartiallyProcessed(100, 80, 10, "10 rows could not be parsed");
        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.PARTIALLY_PROCESSED);
        assertThat(doc.getErrorMessage()).isEqualTo("10 rows could not be parsed");

        doc.markFailed("Corrupt PDF file");
        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(doc.getErrorMessage()).isEqualTo("Corrupt PDF file");
    }

    @Test
    @DisplayName("Transaction entity supports source metadata with default MANUAL source")
    void transactionSourceMetadata() {
        Transaction tx = new Transaction(
                user,
                new BigDecimal("1500.00"),
                "Amazon",
                "Electronics purchase",
                TransactionCategory.SHOPPING,
                TransactionType.EXPENSE,
                null,
                LocalDate.of(2026, 9, 1),
                "Note"
        );

        assertThat(tx.getSourceType()).isEqualTo(TransactionSourceType.MANUAL);
        assertThat(tx.getSourceDocument()).isNull();
        assertThat(tx.getSourceReference()).isNull();

        FinancialDocument doc = new FinancialDocument(
                user,
                "icici.csv",
                DocumentFileType.CSV,
                512L,
                user.getId() + "/doc2.bin",
                "hash456",
                "ICICI",
                DocumentAccountType.CREDIT_CARD,
                "8888",
                null,
                null
        );

        tx.setSourceType(TransactionSourceType.STATEMENT_IMPORT);
        tx.setSourceDocument(doc);
        tx.setSourceReference("row:45");

        assertThat(tx.getSourceType()).isEqualTo(TransactionSourceType.STATEMENT_IMPORT);
        assertThat(tx.getSourceDocument()).isSameAs(doc);
        assertThat(tx.getSourceReference()).isEqualTo("row:45");
    }
}
