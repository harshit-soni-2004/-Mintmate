package com.spendsense.document;

import com.spendsense.common.exception.ApiException;
import com.spendsense.document.dto.DocumentUploadCommand;
import com.spendsense.document.dto.FinancialDocumentResponse;
import com.spendsense.document.storage.DocumentStorage;
import com.spendsense.document.storage.StorageReference;
import com.spendsense.ingestion.SourceTransactionClassification;
import com.spendsense.ingestion.settlement.SettlementStatus;
import com.spendsense.ingestion.settlement.StatementSettlement;
import com.spendsense.ingestion.settlement.StatementSettlementRepository;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionSourceType;
import com.spendsense.transaction.TransactionType;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Phase 7B: FinancialDocumentService Tests")
class FinancialDocumentServiceTest {

    @Mock private FinancialDocumentRepository documentRepository;
    @Mock private DocumentStorage documentStorage;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUser currentUser;
    @Mock private StatementSettlementRepository settlementRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private com.spendsense.ingestion.transfer.TransferMatchingService transferMatchingService;

    private User user;
    private FinancialDocumentService service;

    @BeforeEach
    void setUp() {
        user = new User("user@spendsense.test", "hash", "Alice", "Asia/Kolkata");
        when(currentUser.require()).thenReturn(new SpendSenseUserPrincipal(user));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        service = new FinancialDocumentService(documentRepository, documentStorage, userRepository, currentUser, settlementRepository, transactionRepository, transferMatchingService);
    }

    @Test
    @DisplayName("Uploads document successfully with computed SHA-256 hash and metadata")
    void uploadsDocumentSuccessfully() throws Exception {
        byte[] content = "Statement data for HDFC Bank".getBytes(StandardCharsets.UTF_8);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        String expectedHash = HexFormat.of().formatHex(md.digest(content));

        when(documentStorage.store(eq(user.getId()), any(UUID.class), eq("hdfc_aug.pdf"), any(InputStream.class), eq((long) content.length)))
                .thenAnswer(inv -> {
                    // Consume input stream to simulate storage write
                    InputStream is = inv.getArgument(3);
                    is.readAllBytes();
                    return new StorageReference(user.getId() + "/doc-1.bin", "hdfc_aug.pdf", content.length);
                });

        when(documentRepository.existsByUserIdAndContentHash(user.getId(), expectedHash)).thenReturn(false);
        when(documentRepository.save(any(FinancialDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentUploadCommand command = new DocumentUploadCommand(
                "hdfc_aug.pdf",
                DocumentAccountType.SAVINGS,
                "HDFC Bank",
                "1234",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                new ByteArrayInputStream(content),
                content.length
        );

        FinancialDocumentResponse response = service.upload(command);

        assertThat(response).isNotNull();
        assertThat(response.originalFilename()).isEqualTo("hdfc_aug.pdf");
        assertThat(response.fileType()).isEqualTo(DocumentFileType.PDF);
        assertThat(response.accountType()).isEqualTo(DocumentAccountType.SAVINGS);
        assertThat(response.institutionName()).isEqualTo("HDFC Bank");
        assertThat(response.accountNumberLast4()).isEqualTo("1234");
        assertThat(response.status()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(response.contentHash()).isEqualTo(expectedHash);
        assertThat(response.fileSizeBytes()).isEqualTo(content.length);

        ArgumentCaptor<FinancialDocument> captor = ArgumentCaptor.forClass(FinancialDocument.class);
        verify(documentRepository).save(captor.capture());
        FinancialDocument saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getContentHash()).isEqualTo(expectedHash);
    }

    @Test
    @DisplayName("Rejects upload if identical content hash was already uploaded by the same user")
    void rejectsDuplicateDocumentUpload() throws Exception {
        byte[] content = "Duplicate statement content".getBytes(StandardCharsets.UTF_8);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        String hash = HexFormat.of().formatHex(md.digest(content));

        when(documentStorage.store(eq(user.getId()), any(UUID.class), any(), any(), anyLong()))
                .thenAnswer(inv -> {
                    InputStream is = inv.getArgument(3);
                    is.readAllBytes();
                    return new StorageReference(user.getId() + "/dup.bin", "stmt.csv", content.length);
                });

        when(documentRepository.existsByUserIdAndContentHash(user.getId(), hash)).thenReturn(true);

        DocumentUploadCommand command = new DocumentUploadCommand(
                "stmt.csv",
                DocumentAccountType.CREDIT_CARD,
                "ICICI",
                "9999",
                null,
                null,
                new ByteArrayInputStream(content),
                content.length
        );

        assertThatThrownBy(() -> service.upload(command))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("DUPLICATE_DOCUMENT");

        // Verifies the stored duplicate file was immediately cleaned up
        verify(documentStorage).delete(eq(user.getId()), any(StorageReference.class));
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rejects upload with unsupported file extension")
    void rejectsUnsupportedFileType() {
        byte[] content = "malicious script".getBytes(StandardCharsets.UTF_8);
        DocumentUploadCommand command = new DocumentUploadCommand(
                "virus.exe",
                DocumentAccountType.SAVINGS,
                "Unknown",
                null,
                null,
                null,
                new ByteArrayInputStream(content),
                content.length
        );

        assertThatThrownBy(() -> service.upload(command))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("INVALID_FILE_TYPE");
    }

    @Test
    @DisplayName("Handles storage failure cleanly with INTERNAL_SERVER_ERROR")
    void handlesStorageFailure() throws Exception {
        byte[] content = "Test data".getBytes(StandardCharsets.UTF_8);
        when(documentStorage.store(any(), any(), any(), any(), anyLong()))
                .thenThrow(new IOException("Disk write failure"));

        DocumentUploadCommand command = new DocumentUploadCommand(
                "valid.xlsx",
                DocumentAccountType.CURRENT,
                "SBI",
                null,
                null,
                null,
                new ByteArrayInputStream(content),
                content.length
        );

        assertThatThrownBy(() -> service.upload(command))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("STORAGE_ERROR");
    }

    @Test
    @DisplayName("Retrieves document metadata for authenticated owner")
    void retrievesOwnedDocument() {
        FinancialDocument doc = createSampleDoc("sample.pdf");
        when(documentRepository.findByIdAndUserId(doc.getId(), user.getId())).thenReturn(Optional.of(doc));

        FinancialDocumentResponse response = service.get(doc.getId());
        assertThat(response.id()).isEqualTo(doc.getId());
        assertThat(response.originalFilename()).isEqualTo("sample.pdf");
    }

    @Test
    @DisplayName("User A cannot view User B's document (throws DOCUMENT_NOT_FOUND)")
    void userCannotRetrieveAnotherUsersDocument() {
        UUID docId = UUID.randomUUID();
        when(documentRepository.findByIdAndUserId(docId, user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(docId))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("DOCUMENT_NOT_FOUND");
    }

    @Test
    @DisplayName("Lists all documents for authenticated user")
    void listsUserDocuments() {
        FinancialDocument doc1 = createSampleDoc("stmt1.pdf");
        FinancialDocument doc2 = createSampleDoc("stmt2.csv");
        when(documentRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()))
                .thenReturn(List.of(doc1, doc2));

        List<FinancialDocumentResponse> list = service.list();
        assertThat(list).hasSize(2);
        assertThat(list.get(0).originalFilename()).isEqualTo("stmt1.pdf");
        assertThat(list.get(1).originalFilename()).isEqualTo("stmt2.csv");
    }

    @Test
    @DisplayName("Test A: Deleting statement deletes document and its source-imported transactions")
    void deleteStatement_deletesDocumentAndSourceImportedTransactions() throws Exception {
        FinancialDocument doc = createSampleDoc("stmt.pdf");
        when(documentRepository.findByIdAndUserId(doc.getId(), user.getId())).thenReturn(Optional.of(doc));

        Transaction tx1 = new Transaction(user, new BigDecimal("100.00"), "Merchant 1", null,
                TransactionCategory.FOOD, TransactionType.EXPENSE, null, LocalDate.now(), null,
                null, null, null, false, null, null,
                TransactionSourceType.STATEMENT_IMPORT, doc, "ref-1");
        Transaction tx2 = new Transaction(user, new BigDecimal("200.00"), "Merchant 2", null,
                TransactionCategory.BILLS, TransactionType.EXPENSE, null, LocalDate.now(), null,
                null, null, null, false, null, null,
                TransactionSourceType.STATEMENT_IMPORT, doc, "ref-2");

        when(transactionRepository.findBySourceDocumentIdAndUserId(doc.getId(), user.getId()))
                .thenReturn(List.of(tx1, tx2));

        service.delete(doc.getId());

        verify(transactionRepository).deleteAll(List.of(tx1, tx2));
        verify(transactionRepository).flush();
        verify(documentRepository).delete(doc);
        verify(documentRepository).flush();
        verify(documentStorage).delete(eq(user.getId()), any(StorageReference.class));
    }

    @Test
    @DisplayName("Test B: Manual transactions are NOT deleted even if returned for document")
    void deleteStatement_preservesManualTransactions() {
        FinancialDocument doc = createSampleDoc("stmt.pdf");
        when(documentRepository.findByIdAndUserId(doc.getId(), user.getId())).thenReturn(Optional.of(doc));

        Transaction manualTx = new Transaction(user, new BigDecimal("50.00"), "Cash store", null,
                TransactionCategory.SHOPPING, TransactionType.EXPENSE, null, LocalDate.now(), null,
                null, null, null, false, null, null,
                TransactionSourceType.MANUAL, doc, null);
        Transaction importedTx = new Transaction(user, new BigDecimal("150.00"), "Online store", null,
                TransactionCategory.SHOPPING, TransactionType.EXPENSE, null, LocalDate.now(), null,
                null, null, null, false, null, null,
                TransactionSourceType.STATEMENT_IMPORT, doc, "ref-3");

        when(transactionRepository.findBySourceDocumentIdAndUserId(doc.getId(), user.getId()))
                .thenReturn(List.of(manualTx, importedTx));

        service.delete(doc.getId());

        // Only importedTx should be deleted, manualTx must be preserved
        verify(transactionRepository).deleteAll(List.of(importedTx));
        verify(transactionRepository).flush();
    }

    @Test
    @DisplayName("Test C: Transactions from other statements are not queried or deleted")
    void deleteStatement_doesNotDeleteTransactionsFromOtherStatements() {
        FinancialDocument doc = createSampleDoc("stmt1.pdf");
        when(documentRepository.findByIdAndUserId(doc.getId(), user.getId())).thenReturn(Optional.of(doc));
        when(transactionRepository.findBySourceDocumentIdAndUserId(doc.getId(), user.getId())).thenReturn(List.of());

        service.delete(doc.getId());

        // Verifies query strictly scopes to doc.getId() and current user
        verify(transactionRepository).findBySourceDocumentIdAndUserId(doc.getId(), user.getId());
        verify(transactionRepository, never()).deleteAll(anyList());
    }

    @Test
    @DisplayName("Test D: Settlements are deleted and counterpart settlements in other docs are marked UNRESOLVED")
    void deleteStatement_deletesSettlementsAndUnmatchesCounterpartsInOtherDocuments() {
        FinancialDocument doc1 = createSampleDoc("doc1.pdf");
        FinancialDocument doc2 = createSampleDoc("doc2.pdf");

        StatementSettlement settlement1 = new StatementSettlement(
                user, doc1, 1, LocalDate.of(2026, 8, 10), new BigDecimal("5000.00"),
                "CC PAYMENT", "CC PAYMENT", SourceTransactionClassification.PAYMENT,
                DocumentAccountType.SAVINGS, null, SettlementStatus.RESOLVED);

        StatementSettlement counterpart = new StatementSettlement(
                user, doc2, 1, LocalDate.of(2026, 8, 10), new BigDecimal("5000.00"),
                "PAYMENT RECEIVED", "PAYMENT RECEIVED", SourceTransactionClassification.PAYMENT,
                DocumentAccountType.CREDIT_CARD, null, SettlementStatus.RESOLVED);

        settlement1.markResolved(counterpart.getId());
        counterpart.markResolved(settlement1.getId());

        when(documentRepository.findByIdAndUserId(doc1.getId(), user.getId())).thenReturn(Optional.of(doc1));
        when(settlementRepository.findByDocumentId(doc1.getId())).thenReturn(List.of(settlement1));
        when(settlementRepository.findById(counterpart.getId())).thenReturn(Optional.of(counterpart));

        service.delete(doc1.getId());

        // Counterpart in doc2 should be set to UNRESOLVED and cleared of matched ID
        assertThat(counterpart.getStatus()).isEqualTo(SettlementStatus.UNRESOLVED);
        assertThat(counterpart.getMatchedSettlementId()).isNull();
        verify(settlementRepository).save(counterpart);

        // Document 1's settlement is deleted
        verify(settlementRepository).deleteAll(List.of(settlement1));
        verify(settlementRepository).flush();
    }

    @Test
    @DisplayName("Test E: Deleting non-existent document throws DOCUMENT_NOT_FOUND")
    void deleteStatement_nonExistentDocument_throwsDocumentNotFound() {
        UUID nonExistentId = UUID.randomUUID();
        when(documentRepository.findByIdAndUserId(nonExistentId, user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(nonExistentId))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("DOCUMENT_NOT_FOUND");

        verify(documentRepository, never()).delete(any(FinancialDocument.class));
    }

    @Test
    @DisplayName("Test F: User A cannot delete User B's document (throws DOCUMENT_NOT_FOUND)")
    void deleteStatement_otherUsersDocument_throwsDocumentNotFound() {
        UUID otherUserDocId = UUID.randomUUID();
        when(documentRepository.findByIdAndUserId(otherUserDocId, user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(otherUserDocId))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("DOCUMENT_NOT_FOUND");

        verify(documentRepository, never()).delete(any(FinancialDocument.class));
        verifyNoInteractions(documentStorage);
    }

    @Test
    @DisplayName("Test G: Physical file is deleted via DocumentStorage")
    void deleteStatement_deletesPhysicalFileViaStorage() throws Exception {
        FinancialDocument doc = createSampleDoc("file_to_delete.pdf");
        when(documentRepository.findByIdAndUserId(doc.getId(), user.getId())).thenReturn(Optional.of(doc));

        service.delete(doc.getId());

        ArgumentCaptor<StorageReference> captor = ArgumentCaptor.forClass(StorageReference.class);
        verify(documentStorage).delete(eq(user.getId()), captor.capture());
        StorageReference ref = captor.getValue();
        assertThat(ref.storageKey()).isEqualTo(doc.getStorageKey());
        assertThat(ref.originalFilename()).isEqualTo(doc.getOriginalFilename());
    }

    @Test
    @DisplayName("Test H: Document deletion enables re-uploading identical file without duplicate conflict")
    void deleteStatement_enablesReuploadingWithoutDuplicateConflict() throws Exception {
        byte[] content = "Statement content for re-upload".getBytes(StandardCharsets.UTF_8);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        String contentHash = HexFormat.of().formatHex(md.digest(content));

        FinancialDocument doc = new FinancialDocument(
                user, "reupload.pdf", DocumentFileType.PDF, (long) content.length,
                user.getId() + "/reupload.bin", contentHash, "Bank",
                DocumentAccountType.SAVINGS, "1111", null, null
        );

        when(documentRepository.findByIdAndUserId(doc.getId(), user.getId())).thenReturn(Optional.of(doc));
        // Delete the document
        service.delete(doc.getId());
        verify(documentRepository).delete(doc);

        // After deletion, existsByUserIdAndContentHash returns false
        when(documentRepository.existsByUserIdAndContentHash(user.getId(), contentHash)).thenReturn(false);
        when(documentStorage.store(eq(user.getId()), any(UUID.class), eq("reupload.pdf"), any(), eq((long) content.length)))
                .thenAnswer(inv -> {
                    InputStream is = inv.getArgument(3);
                    is.readAllBytes();
                    return new StorageReference(user.getId() + "/reupload2.bin", "reupload.pdf", content.length);
                });
        when(documentRepository.save(any(FinancialDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentUploadCommand command = new DocumentUploadCommand(
                "reupload.pdf", DocumentAccountType.SAVINGS, "Bank", "1111", null, null,
                new ByteArrayInputStream(content), content.length
        );

        FinancialDocumentResponse response = service.upload(command);
        assertThat(response).isNotNull();
        assertThat(response.contentHash()).isEqualTo(contentHash);
    }

    @Test
    @DisplayName("Test I: Deleting a statement unlinks counterpart TRANSFER in another statement; counterpart is preserved as TRANSFER/UNMATCHED")
    void deleteStatement_unlinksBilateralTransferCounterpart_andPreservesCounterpartTransaction() {
        // Setup: two statements from two different accounts
        FinancialDocument kotakDoc = createSampleDoc("kotak.pdf");   // statement being deleted
        FinancialDocument sbiDoc   = createSampleDoc("sbi.pdf");     // statement that stays

        // The Kotak statement has a TRANSFER OUT for ₹15,000
        Transaction kotakOut = new Transaction(user, new BigDecimal("15000.00"), "UPI/HARSHIT/658828783145/UPI", null,
                TransactionCategory.OTHER, TransactionType.TRANSFER, null, LocalDate.now(), null,
                null, null, null, false, null, null,
                TransactionSourceType.STATEMENT_IMPORT, kotakDoc, "doc:kotak:row:5");

        // The SBI statement has a TRANSFER IN for ₹15,000 (the bilateral counterpart)
        Transaction sbiIn = new Transaction(user, new BigDecimal("15000.00"), "DEP TFR UPI/CR/658828783145", null,
                TransactionCategory.OTHER, TransactionType.TRANSFER, null, LocalDate.now(), null,
                null, null, null, false, null, null,
                TransactionSourceType.STATEMENT_IMPORT, sbiDoc, "doc:sbi:row:12");

        // Simulate unlinkBeforeDeletion resetting sbiIn to TRANSFER/UNMATCHED (what real code does)
        doAnswer(invocation -> {
            sbiIn.setTransferMatchStatus(com.spendsense.transaction.TransferMatchStatus.UNMATCHED);
            sbiIn.unlinkTransfer();
            return null;
        }).when(transferMatchingService).unlinkBeforeDeletion(List.of(kotakOut));

        when(documentRepository.findByIdAndUserId(kotakDoc.getId(), user.getId())).thenReturn(Optional.of(kotakDoc));
        when(transactionRepository.findBySourceDocumentIdAndUserId(kotakDoc.getId(), user.getId()))
                .thenReturn(List.of(kotakOut));

        service.delete(kotakDoc.getId());

        // Only the Kotak TRANSFER OUT should be deleted; unlinkBeforeDeletion invoked first
        verify(transactionRepository).deleteAll(List.of(kotakOut));
        verify(transferMatchingService).unlinkBeforeDeletion(List.of(kotakOut));

        // SBI counterpart was preserved and correctly reset to UNMATCHED, not deleted
        assertThat(sbiIn.getTransferMatchStatus()).isEqualTo(com.spendsense.transaction.TransferMatchStatus.UNMATCHED);
        assertThat(sbiIn.getLinkedTransaction()).isNull();
    }

    @Test
    @DisplayName("Updates document status through lifecycle transitions")
    void updatesStatusLifecycle() {
        FinancialDocument doc = createSampleDoc("lifecycle.pdf");
        when(documentRepository.findByIdAndUserId(doc.getId(), user.getId())).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateStatus(doc.getId(), DocumentStatus.PARSING, 0, 0, 0, null);
        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.PARSING);

        service.updateStatus(doc.getId(), DocumentStatus.COMPLETED, 150, 140, 10, null);
        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(doc.getTotalRowsDetected()).isEqualTo(150);
        assertThat(doc.getImportedCount()).isEqualTo(140);
        assertThat(doc.getDuplicateCount()).isEqualTo(10);
    }

    private FinancialDocument createSampleDoc(String filename) {
        DocumentFileType type = DocumentFileType.fromExtensionOrFilename(filename);
        return new FinancialDocument(
                user,
                filename,
                type,
                1024L,
                user.getId() + "/sample.bin",
                "samplehash123",
                "HDFC",
                DocumentAccountType.SAVINGS,
                "5678",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        );
    }
}
