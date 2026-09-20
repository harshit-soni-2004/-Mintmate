package com.spendsense.ingestion;

/**
 * Represents the source-level financial event classification observed on the statement or payment rail,
 * distinct from the eventual SpendSense TransactionType (EXPENSE/INCOME).
 * Prevents premature classification of card settlements, internal transfers, and refunds as ordinary income/expense.
 */
public enum SourceTransactionClassification {
    PURCHASE,
    PAYMENT,
    REFUND,
    REVERSAL,
    FEE,
    INTEREST,
    TRANSFER,
    DEPOSIT,
    WITHDRAWAL,
    UNKNOWN
}
