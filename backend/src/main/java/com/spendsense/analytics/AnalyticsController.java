package com.spendsense.analytics;

import com.spendsense.analytics.dto.CategoryBreakdownResponse;
import com.spendsense.analytics.dto.FinancialInsight;
import com.spendsense.analytics.dto.InsightsOverviewResponse;
import com.spendsense.analytics.dto.PeriodComparisonResponse;
import com.spendsense.analytics.dto.SpendingAnomaly;
import com.spendsense.analytics.dto.SpendingSummaryResponse;
import com.spendsense.analytics.dto.SpendingTrendResponse;
import com.spendsense.common.api.ApiResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/insights")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final InsightService insightService;
    private final AnomalyDetectionService anomalyDetectionService;

    public AnalyticsController(AnalyticsService analyticsService,
                               InsightService insightService,
                               AnomalyDetectionService anomalyDetectionService) {
        this.analyticsService = analyticsService;
        this.insightService = insightService;
        this.anomalyDetectionService = anomalyDetectionService;
    }

    @GetMapping
    public ApiResponse<InsightsOverviewResponse> getOverview(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) AggregationPeriod trendPeriod) {

        LocalDate[] range = resolveRange(month, fromDate, toDate);
        SpendingSummaryResponse summary = analyticsService.getSummary(range[0], range[1]);
        PeriodComparisonResponse comparison = analyticsService.getComparison(range[0], range[1]);
        CategoryBreakdownResponse categories = analyticsService.getCategoryBreakdown(range[0], range[1]);
        SpendingTrendResponse trends = analyticsService.getTrends(trendPeriod, range[0], range[1]);
        List<FinancialInsight> insights = insightService.generateInsights(range[0], range[1]);
        List<SpendingAnomaly> anomalies = anomalyDetectionService.detectAnomalies(range[0], range[1]);

        return ApiResponse.of(new InsightsOverviewResponse(
                summary,
                comparison,
                categories,
                trends,
                insights,
                anomalies
        ));
    }

    @GetMapping("/summary")
    public ApiResponse<SpendingSummaryResponse> getSummary(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        LocalDate[] range = resolveRange(month, fromDate, toDate);
        return ApiResponse.of(analyticsService.getSummary(range[0], range[1]));
    }

    @GetMapping("/comparison")
    public ApiResponse<PeriodComparisonResponse> getComparison(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        LocalDate[] range = resolveRange(month, fromDate, toDate);
        return ApiResponse.of(analyticsService.getComparison(range[0], range[1]));
    }

    @GetMapping("/categories")
    public ApiResponse<CategoryBreakdownResponse> getCategories(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        LocalDate[] range = resolveRange(month, fromDate, toDate);
        return ApiResponse.of(analyticsService.getCategoryBreakdown(range[0], range[1]));
    }

    @GetMapping("/trends")
    public ApiResponse<SpendingTrendResponse> getTrends(
            @RequestParam(required = false) AggregationPeriod period,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        LocalDate[] range = resolveRange(month, fromDate, toDate);
        return ApiResponse.of(analyticsService.getTrends(period, range[0], range[1]));
    }

    @GetMapping("/anomalies")
    public ApiResponse<List<SpendingAnomaly>> getAnomalies(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        LocalDate[] range = resolveRange(month, fromDate, toDate);
        return ApiResponse.of(anomalyDetectionService.detectAnomalies(range[0], range[1]));
    }

    @GetMapping("/items")
    public ApiResponse<List<FinancialInsight>> getInsights(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        LocalDate[] range = resolveRange(month, fromDate, toDate);
        return ApiResponse.of(insightService.generateInsights(range[0], range[1]));
    }

    private LocalDate[] resolveRange(String month, LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null) {
            return analyticsService.normalizeRange(fromDate, toDate);
        }
        if (month != null && !month.isBlank()) {
            try {
                YearMonth ym = YearMonth.parse(month.trim());
                return new LocalDate[]{ym.atDay(1), ym.atEndOfMonth()};
            } catch (Exception ignored) {}
        }
        return analyticsService.normalizeRange(null, null);
    }
}
