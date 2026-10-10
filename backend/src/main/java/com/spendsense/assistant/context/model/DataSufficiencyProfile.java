package com.spendsense.assistant.context.model;

public record DataSufficiencyProfile(
        DataSufficiency level,
        int historyMonths,
        long transactionCount,
        boolean hasIncomeData,
        boolean hasCategoryData,
        String explanation
) {}
