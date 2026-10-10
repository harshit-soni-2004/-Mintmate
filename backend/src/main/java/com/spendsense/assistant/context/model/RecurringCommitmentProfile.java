package com.spendsense.assistant.context.model;

import com.spendsense.assistant.dto.RecurringExpenseItem;

import java.math.BigDecimal;
import java.util.List;

public record RecurringCommitmentProfile(
        BigDecimal totalMonthlyCommitted,
        int totalRecurringCount,
        List<RecurringExpenseItem> commitments,
        DataSufficiency sufficiency
) {}
