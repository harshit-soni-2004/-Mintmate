package com.spendsense.ingestion.service;

import java.util.List;
import java.util.UUID;

/**
 * Result model representing the summary outcome of a statement document ingestion.
 */
public record StatementImportResult(
        UUID documentId,
        int totalRows,
        int validRows,
        int invalidRows,
        int importedTransactions,
        int exactDuplicates,
        int probableDuplicates,
        int unresolvedCandidates,
        int settlementsResolved,
        int skippedRows,
        List<String> warnings
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID documentId;
        private int totalRows;
        private int validRows;
        private int invalidRows;
        private int importedTransactions;
        private int exactDuplicates;
        private int probableDuplicates;
        private int unresolvedCandidates;
        private int settlementsResolved;
        private int skippedRows;
        private List<String> warnings = List.of();

        public Builder documentId(UUID documentId) {
            this.documentId = documentId;
            return this;
        }

        public Builder totalRows(int totalRows) {
            this.totalRows = totalRows;
            return this;
        }

        public Builder validRows(int validRows) {
            this.validRows = validRows;
            return this;
        }

        public Builder invalidRows(int invalidRows) {
            this.invalidRows = invalidRows;
            return this;
        }

        public Builder importedTransactions(int importedTransactions) {
            this.importedTransactions = importedTransactions;
            return this;
        }

        public Builder exactDuplicates(int exactDuplicates) {
            this.exactDuplicates = exactDuplicates;
            return this;
        }

        public Builder probableDuplicates(int probableDuplicates) {
            this.probableDuplicates = probableDuplicates;
            return this;
        }

        public Builder unresolvedCandidates(int unresolvedCandidates) {
            this.unresolvedCandidates = unresolvedCandidates;
            return this;
        }

        public Builder settlementsResolved(int settlementsResolved) {
            this.settlementsResolved = settlementsResolved;
            return this;
        }

        public Builder skippedRows(int skippedRows) {
            this.skippedRows = skippedRows;
            return this;
        }

        public Builder warnings(List<String> warnings) {
            this.warnings = warnings != null ? warnings : List.of();
            return this;
        }

        public StatementImportResult build() {
            return new StatementImportResult(
                    documentId,
                    totalRows,
                    validRows,
                    invalidRows,
                    importedTransactions,
                    exactDuplicates,
                    probableDuplicates,
                    unresolvedCandidates,
                    settlementsResolved,
                    skippedRows,
                    warnings
            );
        }
    }
}
