package com.spendsense.ingestion.dedup;

import com.spendsense.transaction.Transaction;

public record DuplicateCheckResult(
        DuplicateType type,
        Transaction matchedExistingTransaction,
        String reason
) {
    public enum DuplicateType {
        UNIQUE,
        EXACT_DUPLICATE,
        PROBABLE_DUPLICATE
    }

    public static DuplicateCheckResult unique() {
        return new DuplicateCheckResult(DuplicateType.UNIQUE, null, null);
    }

    public static DuplicateCheckResult exact(Transaction existing, String reason) {
        return new DuplicateCheckResult(DuplicateType.EXACT_DUPLICATE, existing, reason);
    }

    public static DuplicateCheckResult probable(Transaction existing, String reason) {
        return new DuplicateCheckResult(DuplicateType.PROBABLE_DUPLICATE, existing, reason);
    }

    public boolean isExactDuplicate() {
        return type == DuplicateType.EXACT_DUPLICATE;
    }

    public boolean isProbableDuplicate() {
        return type == DuplicateType.PROBABLE_DUPLICATE;
    }

    public Transaction existingTransaction() {
        return matchedExistingTransaction;
    }
}
