package com.spendsense.document.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

/**
 * Local filesystem implementation of DocumentStorage.
 * Stores documents in partitioned directories: {root}/{userId}/{documentId}.bin
 * strictly preventing path traversal.
 */
@Component
public class LocalFileSystemDocumentStorage implements DocumentStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileSystemDocumentStorage.class);

    private final Path rootPath;

    public LocalFileSystemDocumentStorage(@Value("${spendsense.storage.local-dir:./data/documents}") String rootDirPath) {
        this.rootPath = Paths.get(rootDirPath).toAbsolutePath().normalize();
        init();
    }

    private void init() {
        try {
            Files.createDirectories(rootPath);
            log.info("Initialized local document storage at: {}", rootPath);
        } catch (IOException e) {
            log.error("Failed to initialize document storage directory at: {}", rootPath, e);
            throw new IllegalStateException("Could not initialize document storage at: " + rootPath, e);
        }
    }

    @Override
    public StorageReference store(UUID userId, UUID documentId, String originalFilename, InputStream content, long sizeBytes) throws IOException {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(documentId, "documentId cannot be null");
        Objects.requireNonNull(originalFilename, "originalFilename cannot be null");
        Objects.requireNonNull(content, "content stream cannot be null");

        Path targetFile = resolveFilePath(userId, documentId);
        Files.createDirectories(targetFile.getParent());

        long written = Files.copy(content, targetFile, StandardCopyOption.REPLACE_EXISTING);
        long actualSize = sizeBytes > 0 ? sizeBytes : written;

        // The storage key is relative to root storage: {userId}/{documentId}.bin
        String storageKey = userId + "/" + documentId + ".bin";
        return new StorageReference(storageKey, sanitizeFilename(originalFilename), actualSize);
    }

    @Override
    public InputStream retrieve(UUID userId, StorageReference reference) throws IOException {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(reference, "reference cannot be null");

        Path filePath = resolveFromKey(userId, reference.storageKey());
        if (!Files.isRegularFile(filePath)) {
            throw new FileNotFoundException("Document file not found at: " + reference.storageKey());
        }

        return Files.newInputStream(filePath);
    }

    @Override
    public void delete(UUID userId, StorageReference reference) throws IOException {
        if (userId == null || reference == null || reference.storageKey() == null) {
            return;
        }

        Path filePath = resolveFromKey(userId, reference.storageKey());
        Files.deleteIfExists(filePath);
    }

    @Override
    public boolean exists(UUID userId, StorageReference reference) {
        if (userId == null || reference == null || reference.storageKey() == null) {
            return false;
        }

        try {
            Path filePath = resolveFromKey(userId, reference.storageKey());
            return Files.isRegularFile(filePath);
        } catch (SecurityException e) {
            return false;
        }
    }

    public Path getRootPath() {
        return rootPath;
    }

    private Path resolveFilePath(UUID userId, UUID documentId) {
        // Enforce strict UUID pattern to prevent any path traversal
        String safeUser = userId.toString();
        String safeDoc = documentId.toString() + ".bin";

        Path resolved = rootPath.resolve(safeUser).resolve(safeDoc).normalize();
        validatePathUnderRoot(resolved);
        return resolved;
    }

    private Path resolveFromKey(UUID userId, String storageKey) {
        // Storage key must match expected format: {userId}/{documentId}.bin
        String expectedPrefix = userId.toString() + "/";
        if (!storageKey.startsWith(expectedPrefix)) {
            throw new SecurityException("Storage key " + storageKey + " does not belong to user " + userId);
        }

        Path resolved = rootPath.resolve(storageKey).normalize();
        validatePathUnderRoot(resolved);
        return resolved;
    }

    private void validatePathUnderRoot(Path path) {
        if (!path.startsWith(rootPath)) {
            throw new SecurityException("Attempted path traversal outside storage root: " + path);
        }
    }

    private static String sanitizeFilename(String filename) {
        // Strip directory traversal or path separators from original filename
        return Paths.get(filename).getFileName().toString();
    }
}
