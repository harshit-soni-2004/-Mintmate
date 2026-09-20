package com.spendsense.document.storage;

import java.util.Objects;

/**
 * Immutable value object representing a reference to a stored document in DocumentStorage.
 * Decouples domain services from raw filesystem or object-storage path structures.
 */
public record StorageReference(
        String storageKey,
        String originalFilename,
        long sizeBytes
) {
    public StorageReference {
        Objects.requireNonNull(storageKey, "storageKey cannot be null");
        if (storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey cannot be blank");
        }
        Objects.requireNonNull(originalFilename, "originalFilename cannot be null");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes cannot be negative");
        }
    }
}
