package com.spendsense.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.spendsense.analytics.dto.CategoryAnalysis;
import com.spendsense.analytics.dto.CategoryBreakdownResponse;
import com.spendsense.analytics.dto.PeriodComparisonResponse;
import com.spendsense.analytics.dto.SpendingAnomaly;
import com.spendsense.analytics.dto.SpendingSummaryResponse;
import com.spendsense.analytics.dto.TrendMetric;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.PaymentMethod;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionType;
import com.spendsense.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnomalyDetectionServiceTest {

    @Mock private AnalyticsService analyticsService;
    @Mock private TransactionRepository transactionRepository;
    @Mock private CurrentUser currentUser;

    private User user;
    private AnomalyDetectionService anomalyService;

    @BeforeEach
    void setUp() {
        user = new User("priya@example.com", "hash", "Priya Sharma", "Asia/Kolkata");
        lenient().when(currentUser.require()).thenReturn(new SpendSenseUserPrincipal(user));
        anomalyService = new AnomalyDetectionService(analyticsService, transactionRepository, currentUser);

        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        lenient().when(analyticsService.normalizeRange(any(), any())).thenReturn(new LocalDate[]{from, to});
    }

    @Test
    void detectsLargeTransactionAnomaly() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);

        SpendingSummaryResponse summary = new SpendingSummaryResponse(
                new BigDecimal("50000"), new BigDecimal("20000"), new BigDecimal("30000"),
                new BigDecimal("60"), 10L, new BigDecimal("2000.00"), new BigDecimal("8500.00"), from, to);

        PeriodComparisonResponse comparison = new PeriodComparisonResponse(
                from, to, from.minusMonths(1), to.minusMonths(1),
                null, new TrendMetric(new BigDecimal("20000"), new BigDecimal("20000"), BigDecimal.ZERO, BigDecimal.ZERO, TrendDirection.UNCHANGED),
                null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        CategoryBreakdownResponse categories = new CategoryBreakdownResponse(new BigDecimal("20000"), List.of(), null);

        when(analyticsService.getSummary(from, to)).thenReturn(summary);
        when(analyticsService.getComparison(from, to)).thenReturn(comparison);
        when(analyticsService.getCategoryBreakdown(from, to)).thenReturn(categories);

        // Transaction of 8500 > 3x average of 2000 (6000)
        Transaction largeTx = new Transaction(user, new BigDecimal("8500.00"), "Apple Store", "New Phone",
                TransactionCategory.SHOPPING, TransactionType.EXPENSE, PaymentMethod.CREDIT_CARD,
                LocalDate.of(2026, 9, 15), "Electronics");

        when(transactionRepository.findByUser_IdAndTransactionTypeAndTransactionDateBetweenOrderByAmountDesc(
                user.getId(), TransactionType.EXPENSE, from, to))
                .thenReturn(List.of(largeTx));

        List<SpendingAnomaly> anomalies = anomalyService.detectAnomalies(from, to);

        assertThat(anomalies).hasSize(1);
        SpendingAnomaly anomaly = anomalies.get(0);
        assertThat(anomaly.type()).isEqualTo(AnomalyType.LARGE_TRANSACTION);
        assertThat(anomaly.amount()).isEqualByComparingTo("8500.00");
        assertThat(anomaly.description()).contains("4.3x");
        assertThat(anomaly.reason()).contains("3.0x");
    }

    @Test
    void detectsCategorySpendingSpike() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);

        SpendingSummaryResponse summary = new SpendingSummaryResponse(
                BigDecimal.ZERO, new BigDecimal("15000"), BigDecimal.ZERO, BigDecimal.ZERO, 5L,
                new BigDecimal("3000"), new BigDecimal("3000"), from, to);

        PeriodComparisonResponse comparison = new PeriodComparisonResponse(
                from, to, from.minusMonths(1), to.minusMonths(1),
                null, new TrendMetric(new BigDecimal("15000"), new BigDecimal("15000"), BigDecimal.ZERO, BigDecimal.ZERO, TrendDirection.UNCHANGED),
                null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        // FOOD surged from 3000 to 8000 (+166.7%, +5000 diff > 1000 threshold)
        CategoryAnalysis food = new CategoryAnalysis(
                TransactionCategory.FOOD, new BigDecimal("8000.00"), new BigDecimal("53.33"),
                new BigDecimal("3000.00"), new BigDecimal("5000.00"), new BigDecimal("166.67"),
                TrendDirection.INCREASED, 5L);

        CategoryBreakdownResponse categories = new CategoryBreakdownResponse(
                new BigDecimal("15000"), List.of(food), TransactionCategory.FOOD);

        when(analyticsService.getSummary(from, to)).thenReturn(summary);
        when(analyticsService.getComparison(from, to)).thenReturn(comparison);
        when(analyticsService.getCategoryBreakdown(from, to)).thenReturn(categories);
        when(transactionRepository.findByUser_IdAndTransactionTypeAndTransactionDateBetweenOrderByAmountDesc(any(), any(), any(), any()))
                .thenReturn(List.of());

        List<SpendingAnomaly> anomalies = anomalyService.detectAnomalies(from, to);

        assertThat(anomalies).anyMatch(a -> a.type() == AnomalyType.CATEGORY_SPIKE && a.category() == TransactionCategory.FOOD);
    }

    @Test
    void doesNotFlagNormalSpendingAsAnomaly() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);

        SpendingSummaryResponse summary = new SpendingSummaryResponse(
                BigDecimal.ZERO, new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO, 5L,
                new BigDecimal("2000"), new BigDecimal("2500"), from, to);

        PeriodComparisonResponse comparison = new PeriodComparisonResponse(
                from, to, from.minusMonths(1), to.minusMonths(1),
                null, new TrendMetric(new BigDecimal("10000"), new BigDecimal("9500"), new BigDecimal("500"), new BigDecimal("5.26"), TrendDirection.INCREASED),
                null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        CategoryAnalysis food = new CategoryAnalysis(
                TransactionCategory.FOOD, new BigDecimal("4000.00"), new BigDecimal("40"),
                new BigDecimal("3800.00"), new BigDecimal("200.00"), new BigDecimal("5.26"),
                TrendDirection.INCREASED, 3L);

        CategoryBreakdownResponse categories = new CategoryBreakdownResponse(
                new BigDecimal("10000"), List.of(food), TransactionCategory.FOOD);

        when(analyticsService.getSummary(from, to)).thenReturn(summary);
        when(analyticsService.getComparison(from, to)).thenReturn(comparison);
        when(analyticsService.getCategoryBreakdown(from, to)).thenReturn(categories);

        Transaction normalTx = new Transaction(user, new BigDecimal("2500.00"), "Grocery Store", "Groceries",
                TransactionCategory.FOOD, TransactionType.EXPENSE, PaymentMethod.UPI,
                LocalDate.of(2026, 9, 10), "Regular groceries");

        when(transactionRepository.findByUser_IdAndTransactionTypeAndTransactionDateBetweenOrderByAmountDesc(any(), any(), any(), any()))
                .thenReturn(List.of(normalTx));

        List<SpendingAnomaly> anomalies = anomalyService.detectAnomalies(from, to);

        assertThat(anomalies).isEmpty();
    }
}
