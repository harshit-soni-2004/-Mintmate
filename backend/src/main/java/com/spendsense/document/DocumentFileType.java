package com.spendsense.document;

public enum DocumentFileType {
    PDF,
    CSV,
    XLSX;

    public static DocumentFileType fromExtensionOrFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename cannot be empty");
        }
        String lower = filename.trim().toLowerCase();
        if (lower.endsWith(".pdf")) {
            return PDF;
        } else if (lower.endsWith(".csv")) {
            return CSV;
        } else if (lower.endsWith(".xlsx")) {
            return XLSX;
        }
        throw new IllegalArgumentException("Unsupported file format: " + filename);
    }
}
