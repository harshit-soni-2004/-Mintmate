package com.spendsense.analytics;

import com.spendsense.analytics.dto.CategoryAnalysis;
import com.spendsense.analytics.dto.CategoryBreakdownResponse;
import com.spendsense.analytics.dto.PeriodComparisonResponse;
import com.spendsense.analytics.dto.SpendingAnomaly;
import com.spendsense.analytics.dto.SpendingSummaryResponse;
import com.spendsense.security.CurrentUser;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnomalyDetectionService {

    private final AnalyticsService analyticsService;
    private final TransactionRepository transactionRepository;
    private final CurrentUser currentUser;

    public AnomalyDetectionService(AnalyticsService analyticsService,
                                   TransactionRepository transactionRepository,
                                   CurrentUser currentUser) {
        this.analyticsService = analyticsService;
        this.transactionRepository = transactionRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<SpendingAnomaly> detectAnomalies(LocalDate fromDate, LocalDate toDate) {
        UUID userId = currentUser.require().getUserId();
        LocalDate[] range = analyticsService.normalizeRange(fromDate, toDate);
        LocalDate from = range[0];
        LocalDate to = range[1];

        SpendingSummaryResponse summary = analyticsService.getSummary(from, to);
        PeriodComparisonResponse comparison = analyticsService.getComparison(from, to);
        CategoryBreakdownResponse categories = analyticsService.getCategoryBreakdown(from, to);

        List<SpendingAnomaly> anomalies = new ArrayList<>();

        detectPeriodSpike(comparison, anomalies);
        detectCategorySpikes(categories, anomalies);
        detectLargeTransactions(userId, summary.averageExpense(), from, to, anomalies);

        return anomalies;
    }

    private void detectPeriodSpike(PeriodComparisonResponse comparison, List<SpendingAnomaly> anomalies) {
        BigDecimal curExpenses = comparison.expenses().currentAmount();
        BigDecimal prevExpenses = comparison.expenses().previousAmount();

        if (prevExpenses == null || prevExpenses.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal diff = curExpenses.subtract(prevExpenses);
        BigDecimal pctChange = comparison.expenses().percentageChange();

        if (diff.compareTo(AnalyticsThresholds.MIN_PERIOD_SPIKE_AMOUNT) >= 0 &&
                pctChange != null && pctChange.doubleValue() >= AnalyticsThresholds.PERIOD_SPIKE_PERCENT) {
            anomalies.add(new SpendingAnomaly(
                    "anomaly-period-spike",
                    AnomalyType.PERIOD_SPIKE,
                    "Overall spending spike detected",
                    String.format("Total spending of ₹%.2f is %.1f%% higher than last period's ₹%.2f.",
                            curExpenses, pctChange.doubleValue(), prevExpenses),
                    String.format("Total spending jumped by ₹%.2f (+%.1f%%) exceeding the %.0f%% alert threshold.",
                            diff, pctChange.doubleValue(), AnalyticsThresholds.PERIOD_SPIKE_PERCENT),
                    curExpenses,
                    prevExpenses,
                    pctChange,
                    null,
                    comparison.currentTo(),
                    null
            ));
        }
    }

    private void detectCategorySpikes(CategoryBreakdownResponse categories, List<SpendingAnomaly> anomalies) {
        for (CategoryAnalysis ca : categories.categories()) {
            if (ca.previousAmount() == null || ca.previousAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal diff = ca.changeAmount();
            double pct = ca.percentageChange().doubleValue();

            if (diff.compareTo(AnalyticsThresholds.MIN_CATEGORY_SPIKE_AMOUNT) >= 0 &&
                    pct >= AnalyticsThresholds.CATEGORY_SPIKE_PERCENT) {
                anomalies.add(new SpendingAnomaly(
                        "anomaly-cat-spike-" + ca.category(),
                        AnomalyType.CATEGORY_SPIKE,
                        ca.category() + " spending spike",
                        String.format("%s spending (₹%.2f) surged %.1f%% above the prior period baseline (₹%.2f).",
                                ca.category(), ca.amount(), pct, ca.previousAmount()),
                        String.format("Exceeded normal baseline by ₹%.2f (+%.1f%%). Historical comparison indicates unusually high activity.",
                                diff, pct),
                        ca.amount(),
                        ca.previousAmount(),
                        ca.percentageChange(),
                        ca.category(),
                        null,
                        null
                ));
            }
        }
    }

    private void detectLargeTransactions(UUID userId, BigDecimal avgExpense, LocalDate fromDate, LocalDate toDate, List<SpendingAnomaly> anomalies) {
        if (avgExpense.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        List<Transaction> expenses = transactionRepository.findByUser_IdAndTransactionTypeAndTransactionDateBetweenOrderByAmountDesc(
                userId, TransactionType.EXPENSE, fromDate, toDate);

        BigDecimal threshold = avgExpense.multiply(BigDecimal.valueOf(AnalyticsThresholds.LARGE_TRANSACTION_MULTIPLIER));

        for (Transaction tx : expenses) {
            if (tx.getAmount().compareTo(threshold) >= 0 &&
                    tx.getAmount().compareTo(AnalyticsThresholds.MIN_LARGE_TRANSACTION_AMOUNT) >= 0) {
                BigDecimal multiplier = tx.getAmount().divide(avgExpense, 1, RoundingMode.HALF_UP);
                anomalies.add(new SpendingAnomaly(
                        "anomaly-large-tx-" + tx.getId(),
                        AnomalyType.LARGE_TRANSACTION,
                        "Unusually large transaction at " + tx.getMerchant(),
                        String.format("Payment of ₹%.2f at %s is %sx your average transaction size (₹%.2f).",
                                tx.getAmount(), tx.getMerchant(), multiplier, avgExpense),
                        String.format("Transaction exceeds the %sx average expense threshold (₹%.2f).",
                                AnalyticsThresholds.LARGE_TRANSACTION_MULTIPLIER, threshold),
                        tx.getAmount(),
                        avgExpense,
                        multiplier,
                        tx.getCategory(),
                        tx.getTransactionDate(),
                        tx.getId()
                ));
            }
        }
    }
}
