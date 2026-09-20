package com.spendsense.document.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Storage SPI for managing uploaded financial documents outside MySQL.
 */
public interface DocumentStorage {

    /**
     * Stores an input stream in tenant-isolated storage.
     *
     * @param userId authenticated user ID
     * @param documentId unique financial document ID
     * @param originalFilename client-supplied filename (metadata only, not path)
     * @param content binary input stream
     * @param sizeBytes length in bytes
     * @return storage reference
     * @throws IOException if storage fails
     */
    StorageReference store(UUID userId, UUID documentId, String originalFilename, InputStream content, long sizeBytes) throws IOException;

    /**
     * Retrieves an input stream for an existing stored document.
     *
     * @param userId authenticated user ID
     * @param reference storage reference
     * @return readable input stream
     * @throws IOException if file does not exist or cannot be read
     */
    InputStream retrieve(UUID userId, StorageReference reference) throws IOException;

    /**
     * Deletes a stored document file.
     *
     * @param userId authenticated user ID
     * @param reference storage reference
     * @throws IOException if deletion fails
     */
    void delete(UUID userId, StorageReference reference) throws IOException;

    /**
     * Checks if a document file exists in storage.
     *
     * @param userId authenticated user ID
     * @param reference storage reference
     * @return true if file exists
     */
    boolean exists(UUID userId, StorageReference reference);
}
