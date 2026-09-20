package com.spendsense.ingestion.settlement;

/**
 * Status lifecycle for internal settlements, transfers, and refunds.
 */
public enum SettlementStatus {
    UNRESOLVED,
    RESOLVED,
    DISCARDED
}
