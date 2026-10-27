package com.spendsense.debt;

/**
 * Type of debt from the current user's perspective.
 */
public enum DebtType {
    OWED_TO_ME, // Money someone else owes to the user (receivable)
    I_OWE       // Money the user owes to someone else (payable)
}
