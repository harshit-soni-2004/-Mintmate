package com.spendsense.transaction;

/**
 * Indicates the ingestion channel through which a transaction entered SpendSense.
 * Distinct from CategorizationSource which indicates how the category was determined.
 */
public enum TransactionSourceType {
    MANUAL,
    STATEMENT_IMPORT,
    SMS_IMPORT,
    API_IMPORT
}
