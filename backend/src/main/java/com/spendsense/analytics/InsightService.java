package com.spendsense.analytics;

import com.spendsense.analytics.dto.CategoryAnalysis;
import com.spendsense.analytics.dto.CategoryBreakdownResponse;
import com.spendsense.analytics.dto.FinancialInsight;
import com.spendsense.analytics.dto.PeriodComparisonResponse;
import com.spendsense.analytics.dto.SpendingSummaryResponse;
import com.spendsense.budget.Budget;
import com.spendsense.budget.BudgetRepository;
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
public class InsightService {

    private final AnalyticsService analyticsService;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final CurrentUser currentUser;

    public InsightService(AnalyticsService analyticsService,
                          TransactionRepository transactionRepository,
                          BudgetRepository budgetRepository,
                          CurrentUser currentUser) {
        this.analyticsService = analyticsService;
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<FinancialInsight> generateInsights(LocalDate fromDate, LocalDate toDate) {
        UUID userId = currentUser.require().getUserId();
        LocalDate[] range = analyticsService.normalizeRange(fromDate, toDate);
        LocalDate from = range[0];
        LocalDate to = range[1];

        SpendingSummaryResponse summary = analyticsService.getSummary(from, to);
        PeriodComparisonResponse comparison = analyticsService.getComparison(from, to);
        CategoryBreakdownResponse categories = analyticsService.getCategoryBreakdown(from, to);

        List<FinancialInsight> insights = new ArrayList<>();

        generateBudgetInsights(userId, from, to, insights);
        generateSavingsRateInsights(comparison, insights);
        generateCategoryInsights(categories, insights);
        generateTopCategoryInsight(categories, summary.totalExpenses(), insights);
        generateLargeTransactionInsight(userId, summary.averageExpense(), from, to, insights);

        return insights;
    }

    private void generateBudgetInsights(UUID userId, LocalDate fromDate, LocalDate toDate, List<FinancialInsight> insights) {
        List<Budget> activeBudgets = budgetRepository.findActiveBudgets(userId, fromDate, toDate);

        for (Budget budget : activeBudgets) {
            BigDecimal spent = transactionRepository.sumAmountByUserIdAndCategoryAndTypeAndDateBetween(
                    userId, budget.getCategory(), TransactionType.EXPENSE, budget.getStartDate(), budget.getEndDate());
            BigDecimal limit = budget.getLimitAmount();

            if (limit.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal pct = spent.multiply(BigDecimal.valueOf(100))
                    .divide(limit, 2, RoundingMode.HALF_UP);

            if (pct.doubleValue() >= AnalyticsThresholds.BUDGET_EXCEEDED_THRESHOLD) {
                BigDecimal overAmount = spent.subtract(limit);
                insights.add(new FinancialInsight(
                        "budget-exceeded-" + budget.getId(),
                        InsightType.BUDGET_EXCEEDED,
                        InsightSeverity.CRITICAL,
                        budget.getCategory() + " budget exceeded",
                        String.format("You have exceeded your %s budget by ₹%.2f (%s%% spent).",
                                budget.getCategory(), overAmount, pct),
                        budget.getCategory(),
                        spent,
                        pct,
                        "Pause non-essential " + budget.getCategory() + " expenses to stay on track."
                ));
            } else if (pct.doubleValue() >= AnalyticsThresholds.BUDGET_WARNING_THRESHOLD) {
                BigDecimal remaining = limit.subtract(spent);
                insights.add(new FinancialInsight(
                        "budget-warning-" + budget.getId(),
                        InsightType.BUDGET_WARNING,
                        InsightSeverity.WARNING,
                        budget.getCategory() + " budget at " + pct + "%",
                        String.format("You have spent %s%% of your %s budget. ₹%.2f remaining.",
                                pct, budget.getCategory(), remaining),
                        budget.getCategory(),
                        spent,
                        pct,
                        "Slow down spending in " + budget.getCategory() + " to avoid exceeding your budget."
                ));
            }
        }
    }

    private void generateSavingsRateInsights(PeriodComparisonResponse comparison, List<FinancialInsight> insights) {
        BigDecimal change = comparison.savingsRateChange();
        if (change == null) return;

        double changeVal = change.doubleValue();
        if (changeVal >= AnalyticsThresholds.SAVINGS_RATE_CHANGE_POINTS) {
            insights.add(new FinancialInsight(
                    "savings-improved",
                    InsightType.SAVINGS_IMPROVED,
                    InsightSeverity.POSITIVE,
                    "Savings rate improved",
                    String.format("Your savings rate increased from %.1f%% to %.1f%% (+%.1f%%).",
                            comparison.previousSavingsRate(), comparison.currentSavingsRate(), change),
                    null,
                    comparison.netSavings().currentAmount(),
                    change,
                    "Great work! Consider putting these extra savings toward your goals."
            ));
        } else if (changeVal <= -AnalyticsThresholds.SAVINGS_RATE_CHANGE_POINTS) {
            insights.add(new FinancialInsight(
                    "savings-deteriorated",
                    InsightType.SAVINGS_DETERIORATED,
                    InsightSeverity.WARNING,
                    "Savings rate declined",
                    String.format("Your savings rate dropped from %.1f%% to %.1f%% (%.1f%%).",
                            comparison.previousSavingsRate(), comparison.currentSavingsRate(), change),
                    null,
                    comparison.netSavings().currentAmount(),
                    change,
                    "Review discretionary spending categories to identify potential savings."
            ));
        }
    }

    private void generateCategoryInsights(CategoryBreakdownResponse categories, List<FinancialInsight> insights) {
        for (CategoryAnalysis ca : categories.categories()) {
            if (ca.previousAmount() == null || ca.previousAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal changeAmt = ca.changeAmount().abs();
            double pctChange = ca.percentageChange().doubleValue();

            if (changeAmt.compareTo(AnalyticsThresholds.MIN_CATEGORY_CHANGE_AMOUNT) >= 0) {
                if (pctChange >= AnalyticsThresholds.CATEGORY_CHANGE_PERCENT) {
                    insights.add(new FinancialInsight(
                            "cat-increase-" + ca.category(),
                            InsightType.CATEGORY_INCREASE,
                            InsightSeverity.WARNING,
                            ca.category() + " spending increased",
                            String.format("Your %s spending jumped by %.1f%% (+₹%.2f) compared to the previous period.",
                                    ca.category(), pctChange, ca.changeAmount()),
                            ca.category(),
                            ca.amount(),
                            ca.percentageChange(),
                            "Review your recent " + ca.category() + " purchases for unexpected or recurring costs."
                    ));
                } else if (pctChange <= -AnalyticsThresholds.CATEGORY_CHANGE_PERCENT) {
                    insights.add(new FinancialInsight(
                            "cat-decrease-" + ca.category(),
                            InsightType.CATEGORY_DECREASE,
                            InsightSeverity.POSITIVE,
                            ca.category() + " spending decreased",
                            String.format("You reduced %s spending by %.1f%% (saved ₹%.2f) compared to last period.",
                                    ca.category(), Math.abs(pctChange), changeAmt),
                            ca.category(),
                            ca.amount(),
                            ca.percentageChange(),
                            "Great discipline! Keep up these healthy spending habits."
                    ));
                }
            }
        }
    }

    private void generateTopCategoryInsight(CategoryBreakdownResponse categories, BigDecimal totalExpenses, List<FinancialInsight> insights) {
        if (categories.topCategory() == null || totalExpenses.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        for (CategoryAnalysis ca : categories.categories()) {
            if (ca.category() == categories.topCategory()) {
                if (ca.percentage().doubleValue() >= 25.0) {
                    insights.add(new FinancialInsight(
                            "top-category-" + ca.category(),
                            InsightType.TOP_SPENDING_CATEGORY,
                            InsightSeverity.INFO,
                            ca.category() + " is your largest expense",
                            String.format("%s accounts for %s%% of your total expenses (₹%.2f).",
                                    ca.category(), ca.percentage(), ca.amount()),
                            ca.category(),
                            ca.amount(),
                            ca.percentage(),
                            "Keeping " + ca.category() + " well-budgeted will have the biggest impact on your savings."
                    ));
                }
                break;
            }
        }
    }

    private void generateLargeTransactionInsight(UUID userId, BigDecimal avgExpense, LocalDate fromDate, LocalDate toDate, List<FinancialInsight> insights) {
        if (avgExpense.compareTo(BigDecimal.ZERO) <= 0) return;

        List<Transaction> expenses = transactionRepository.findByUser_IdAndTransactionTypeAndTransactionDateBetweenOrderByAmountDesc(
                userId, TransactionType.EXPENSE, fromDate, toDate);

        if (expenses.isEmpty()) return;

        Transaction top = expenses.get(0);
        BigDecimal threshold = avgExpense.multiply(BigDecimal.valueOf(AnalyticsThresholds.LARGE_TRANSACTION_MULTIPLIER));

        if (top.getAmount().compareTo(threshold) >= 0 &&
                top.getAmount().compareTo(AnalyticsThresholds.MIN_LARGE_TRANSACTION_AMOUNT) >= 0) {
            insights.add(new FinancialInsight(
                    "large-tx-" + top.getId(),
                    InsightType.LARGE_TRANSACTION,
                    InsightSeverity.INFO,
                    "Large transaction: " + top.getMerchant(),
                    String.format("Your single largest expense was ₹%.2f at %s on %s.",
                            top.getAmount(), top.getMerchant(), top.getTransactionDate()),
                    top.getCategory(),
                    top.getAmount(),
                    null,
                    "Verify this was a planned one-off expense."
            ));
        }
    }
}
