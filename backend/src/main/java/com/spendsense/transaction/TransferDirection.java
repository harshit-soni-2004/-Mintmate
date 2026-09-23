package com.spendsense.transaction;

/**
 * Direction of movement for an internal transfer relative to the owning financial account.
 */
public enum TransferDirection {
    IN,   // Money arriving into this account (credit)
    OUT   // Money leaving this account (debit)
}
