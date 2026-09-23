package com.spendsense.transaction;

/**
 * Lifecycle status of internal transfer matching between opposing account legs.
 */
public enum TransferMatchStatus {
    NONE,                 // Ordinary non-transfer transaction
    UNMATCHED,            // Transfer leg with no counterpart statement uploaded yet
    AUTO_MATCHED,         // Paired automatically via deterministic signals (amount, dates, references, accounts)
    MANUALLY_CONFIRMED    // Confirmed by the user
}
