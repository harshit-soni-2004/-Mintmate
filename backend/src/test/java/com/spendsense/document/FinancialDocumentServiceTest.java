package com.spendsense.document;

import com.spendsense.common.exception.ApiException;
import com.spendsense.document.dto.DocumentUploadCommand;
import com.spendsense.document.dto.FinancialDocumentResponse;
import com.spendsense.document.storage.DocumentStorage;
import com.spendsense.document.storage.StorageReference;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
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

    private User user;
    private FinancialDocumentService service;

    @BeforeEach
    void setUp() {
        user = new User("user@spendsense.test", "hash", "Alice", "Asia/Kolkata");
        when(currentUser.require()).thenReturn(new SpendSenseUserPrincipal(user));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        service = new FinancialDocumentService(documentRepository, documentStorage, userRepository, currentUser);
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
    @DisplayName("Deletes document and physical file cleanly")
    void deletesDocumentSuccessfully() throws Exception {
        FinancialDocument doc = createSampleDoc("delete_me.pdf");
        when(documentRepository.findByIdAndUserId(doc.getId(), user.getId())).thenReturn(Optional.of(doc));

        service.delete(doc.getId());

        verify(documentStorage).delete(eq(user.getId()), any(StorageReference.class));
        verify(documentRepository).delete(doc);
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
