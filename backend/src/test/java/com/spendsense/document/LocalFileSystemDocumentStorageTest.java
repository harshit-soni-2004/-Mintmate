package com.spendsense.document;

import com.spendsense.document.storage.LocalFileSystemDocumentStorage;
import com.spendsense.document.storage.StorageReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Phase 7B: LocalFileSystemDocumentStorage Tests")
class LocalFileSystemDocumentStorageTest {

    @TempDir
    Path tempDir;

    private LocalFileSystemDocumentStorage storage;
    private UUID userId;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        storage = new LocalFileSystemDocumentStorage(tempDir.toString());
        userId = UUID.randomUUID();
        documentId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Stores file in tenant-isolated path and retrieves content correctly")
    void storesAndRetrievesFile() throws IOException {
        byte[] content = "Date,Description,Amount\n2026-09-01,Salary,50000".getBytes(StandardCharsets.UTF_8);
        StorageReference ref = storage.store(
                userId,
                documentId,
                "statement.csv",
                new ByteArrayInputStream(content),
                content.length
        );

        assertThat(ref.storageKey()).isEqualTo(userId + "/" + documentId + ".bin");
        assertThat(ref.originalFilename()).isEqualTo("statement.csv");
        assertThat(ref.sizeBytes()).isEqualTo(content.length);

        assertThat(storage.exists(userId, ref)).isTrue();

        try (InputStream in = storage.retrieve(userId, ref)) {
            byte[] retrieved = in.readAllBytes();
            assertThat(retrieved).isEqualTo(content);
        }
    }

    @Test
    @DisplayName("Deletes stored file successfully")
    void deletesFileSuccessfully() throws IOException {
        byte[] content = "Dummy PDF content".getBytes(StandardCharsets.UTF_8);
        StorageReference ref = storage.store(
                userId,
                documentId,
                "statement.pdf",
                new ByteArrayInputStream(content),
                content.length
        );

        assertThat(storage.exists(userId, ref)).isTrue();
        storage.delete(userId, ref);
        assertThat(storage.exists(userId, ref)).isFalse();
    }

    @Test
    @DisplayName("Throws FileNotFoundException when retrieving non-existent file")
    void throwsWhenRetrievingMissingFile() {
        StorageReference nonExistentRef = new StorageReference(
                userId + "/" + UUID.randomUUID() + ".bin",
                "missing.pdf",
                100
        );

        assertThatThrownBy(() -> storage.retrieve(userId, nonExistentRef))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("Enforces tenant isolation: User A cannot retrieve User B's file")
    void enforcesUserIsolation() throws IOException {
        byte[] content = "Confidential data of User A".getBytes(StandardCharsets.UTF_8);
        StorageReference refUserA = storage.store(
                userId,
                documentId,
                "userA_statement.pdf",
                new ByteArrayInputStream(content),
                content.length
        );

        UUID userB = UUID.randomUUID();

        // User B attempts to access User A's storageKey
        assertThatThrownBy(() -> storage.retrieve(userB, refUserA))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("does not belong to user");
    }

    @Test
    @DisplayName("Protects against path traversal in filenames and keys")
    void protectsAgainstPathTraversal() throws IOException {
        byte[] content = "Data".getBytes(StandardCharsets.UTF_8);

        // Filename with path traversal attempt is sanitized to base filename
        StorageReference ref = storage.store(
                userId,
                documentId,
                "../../etc/passwd",
                new ByteArrayInputStream(content),
                content.length
        );

        assertThat(ref.originalFilename()).isEqualTo("passwd");

        // Key with path traversal is blocked
        StorageReference maliciousKeyRef = new StorageReference(
                userId + "/../../outside.bin",
                "hacked.bin",
                10
        );

        assertThatThrownBy(() -> storage.retrieve(userId, maliciousKeyRef))
                .isInstanceOf(SecurityException.class);
    }
}
