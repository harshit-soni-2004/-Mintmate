package com.spendsense.dashboard.dto;

import com.spendsense.transaction.dto.TransactionResponse;
import java.time.LocalDate;
import java.util.List;

public record DashboardDataResponse(
        DashboardSummaryResponse summary,
        List<CategorySpendingResponse> categorySpending,
        List<DailySpendingResponse> dailySpending,
        List<TransactionResponse> recentTransactions,
        List<TransactionResponse> periodTransactions,
        LocalDate fromDate,
        LocalDate toDate
) {
}
