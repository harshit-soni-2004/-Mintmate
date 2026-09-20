package com.spendsense.document;

import com.spendsense.common.api.ApiResponse;
import com.spendsense.common.exception.ApiException;
import com.spendsense.document.dto.DocumentUploadCommand;
import com.spendsense.document.dto.FinancialDocumentResponse;
import com.spendsense.document.dto.StatementUploadResponse;
import com.spendsense.ingestion.SourceTransactionClassification;
import com.spendsense.ingestion.service.StatementImportResult;
import com.spendsense.ingestion.service.StatementIngestionService;
import com.spendsense.ingestion.settlement.SettlementStatus;
import com.spendsense.ingestion.settlement.StatementSettlement;
import com.spendsense.ingestion.settlement.StatementSettlementRepository;
import com.spendsense.ingestion.settlement.dto.StatementSettlementResponse;
import com.spendsense.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatementControllerTest {

    @Mock
    private FinancialDocumentService financialDocumentService;

    @Mock
    private StatementIngestionService statementIngestionService;

    @Mock
    private StatementSettlementRepository settlementRepository;

    private StatementController controller;

    @BeforeEach
    void setUp() {
        controller = new StatementController(
                financialDocumentService,
                statementIngestionService,
                settlementRepository
        );
    }

    private FinancialDocumentResponse createDocResponse(UUID id, DocumentStatus status) {
        return new FinancialDocumentResponse(
                id,
                "statement.pdf",
                DocumentFileType.PDF,
                1024L,
                "hash123",
                "HDFC Bank",
                DocumentAccountType.SAVINGS,
                "1234",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                status,
                10,
                8,
                2,
                null,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    @DisplayName("Should successfully upload and ingest statement file")
    void shouldUploadAndIngestStatementSuccessfully() {
        UUID docId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "statement.pdf", "application/pdf", "dummy pdf content".getBytes());

        FinancialDocumentResponse docResponse = createDocResponse(docId, DocumentStatus.COMPLETED);
        StatementImportResult importResult = StatementImportResult.builder()
                .documentId(docId)
                .totalRows(10)
                .importedTransactions(8)
                .exactDuplicates(2)
                .build();

        when(financialDocumentService.upload(any(DocumentUploadCommand.class))).thenReturn(docResponse);
        when(statementIngestionService.ingestDocument(docId)).thenReturn(importResult);
        when(financialDocumentService.get(docId)).thenReturn(docResponse);

        ApiResponse<StatementUploadResponse> response = controller.uploadAndIngest(
                file, DocumentAccountType.SAVINGS, "HDFC Bank", "1234", null, null);

        assertThat(response.data()).isNotNull();
        assertThat(response.data().document().id()).isEqualTo(docId);
        assertThat(response.data().importResult().importedTransactions()).isEqualTo(8);
    }

    @Test
    @DisplayName("Should reject empty file upload")
    void shouldRejectEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> controller.uploadAndIngest(emptyFile, DocumentAccountType.SAVINGS, null, null, null, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot be empty");
    }

    @Test
    @DisplayName("Should reject file exceeding 15 MB")
    void shouldRejectFileExceedingMaxSize() {
        byte[] largeBytes = new byte[16 * 1024 * 1024]; // 16 MB
        MockMultipartFile largeFile = new MockMultipartFile("file", "large.pdf", "application/pdf", largeBytes);

        assertThatThrownBy(() -> controller.uploadAndIngest(largeFile, DocumentAccountType.SAVINGS, null, null, null, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("15 MB limit");
    }

    @Test
    @DisplayName("Should list statements for current user")
    void shouldListStatements() {
        FinancialDocumentResponse doc = createDocResponse(UUID.randomUUID(), DocumentStatus.COMPLETED);
        when(financialDocumentService.list()).thenReturn(List.of(doc));

        ApiResponse<List<FinancialDocumentResponse>> response = controller.list();

        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).originalFilename()).isEqualTo("statement.pdf");
    }

    @Test
    @DisplayName("Should get single statement by id")
    void shouldGetStatementById() {
        UUID docId = UUID.randomUUID();
        FinancialDocumentResponse doc = createDocResponse(docId, DocumentStatus.COMPLETED);
        when(financialDocumentService.get(docId)).thenReturn(doc);

        ApiResponse<FinancialDocumentResponse> response = controller.get(docId);

        assertThat(response.data().id()).isEqualTo(docId);
    }

    @Test
    @DisplayName("Should list settlements for a document")
    void shouldListSettlementsForStatement() {
        UUID docId = UUID.randomUUID();
        User user = new User("test@spendsense.com", "hash", "Test", "UTC");
        FinancialDocument doc = new FinancialDocument(
                user, "statement.pdf", DocumentFileType.PDF, 1024, "key", "hash",
                "HDFC", DocumentAccountType.SAVINGS, "1234", null, null);

        StatementSettlement settlement = new StatementSettlement(
                user, doc, 1, LocalDate.of(2026, 3, 10), new BigDecimal("15000.00"),
                "HDFC CC PAYMENT", "HDFC CC PAYMENT", SourceTransactionClassification.PAYMENT,
                DocumentAccountType.SAVINGS, null, SettlementStatus.UNRESOLVED);

        when(financialDocumentService.get(docId)).thenReturn(createDocResponse(docId, DocumentStatus.COMPLETED));
        when(settlementRepository.findByDocumentId(docId)).thenReturn(List.of(settlement));

        ApiResponse<List<StatementSettlementResponse>> response = controller.listSettlements(docId);

        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).amount()).isEqualByComparingTo("15000.00");
    }

    @Test
    @DisplayName("Should re-ingest statement by id returning StatementUploadResponse")
    void shouldReingestStatement() {
        UUID docId = UUID.randomUUID();
        FinancialDocumentResponse docResponse = createDocResponse(docId, DocumentStatus.COMPLETED);
        StatementImportResult importResult = StatementImportResult.builder()
                .documentId(docId)
                .totalRows(10)
                .importedTransactions(10)
                .build();

        when(statementIngestionService.ingestDocument(docId)).thenReturn(importResult);
        when(financialDocumentService.get(docId)).thenReturn(docResponse);

        ApiResponse<StatementUploadResponse> response = controller.ingest(docId);

        assertThat(response.data()).isNotNull();
        assertThat(response.data().document().id()).isEqualTo(docId);
        assertThat(response.data().importResult().importedTransactions()).isEqualTo(10);
    }

    @Test
    @DisplayName("Should delete statement by id")
    void shouldDeleteStatement() {
        UUID docId = UUID.randomUUID();
        doNothing().when(financialDocumentService).delete(docId);

        controller.delete(docId);

        verify(financialDocumentService).delete(docId);
    }
}
