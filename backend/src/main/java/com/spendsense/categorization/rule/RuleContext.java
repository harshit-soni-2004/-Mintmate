package com.spendsense.categorization.rule;

import com.spendsense.categorization.NormalizedTransaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Contextual transaction information provided to deterministic categorization rules.
 */
public record RuleContext(
        UUID userId,
        NormalizedTransaction normalizedTransaction,
        BigDecimal amount,
        String paymentMethod,
        LocalDate transactionDate
) {}
