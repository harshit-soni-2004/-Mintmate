package com.spendsense.analytics.dto;

import java.util.List;

public record InsightsOverviewResponse(
                SpendingSummaryResponse summary,
                PeriodComparisonResponse comparison,
                CategoryBreakdownResponse categories,
                SpendingTrendResponse trends,
                List<FinancialInsight> insights,
                List<SpendingAnomaly> anomalies) {
}
