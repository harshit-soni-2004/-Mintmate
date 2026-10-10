package com.spendsense.assistant.proactive;

import com.spendsense.analytics.AnalyticsService;
import com.spendsense.analytics.AnomalyDetectionService;
import com.spendsense.analytics.AnomalyType;
import com.spendsense.analytics.InsightSeverity;
import com.spendsense.analytics.dto.CategoryAnalysis;
import com.spendsense.analytics.dto.CategoryBreakdownResponse;
import com.spendsense.analytics.dto.PeriodComparisonResponse;
import com.spendsense.analytics.dto.SpendingAnomaly;
import com.spendsense.assistant.context.FinancialContextService;
import com.spendsense.assistant.context.model.CategoryProfile;
import com.spendsense.assistant.context.model.FinancialProfile;
import com.spendsense.budget.BudgetService;
import com.spendsense.budget.dto.BudgetResponse;
import com.spendsense.budget.dto.BudgetSummaryResponse;
import com.spendsense.goal.GoalService;
import com.spendsense.goal.dto.GoalResponse;
import com.spendsense.security.SpendSenseUserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class ProactiveInsightDetector {

    private static final Logger log = LoggerFactory.getLogger(ProactiveInsightDetector.class);

    private final FinancialContextService financialContextService;
    private final AnalyticsService analyticsService;
    private final BudgetService budgetService;
    private final GoalService goalService;
    private final AnomalyDetectionService anomalyDetectionService;

    public ProactiveInsightDetector(
            FinancialContextService financialContextService,
            AnalyticsService analyticsService,
            BudgetService budgetService,
            GoalService goalService,
            AnomalyDetectionService anomalyDetectionService
    ) {
        this.financialContextService = financialContextService;
        this.analyticsService = analyticsService;
        this.budgetService = budgetService;
        this.goalService = goalService;
        this.anomalyDetectionService = anomalyDetectionService;
    }

    public List<ProactiveInsightCandidate> detectCandidates(SpendSenseUserPrincipal user) {
        List<ProactiveInsightCandidate> candidates = new ArrayList<>();
        YearMonth currentYm = YearMonth.now();
        LocalDate monthStart = currentYm.atDay(1);
        LocalDate today = LocalDate.now();

        try {
            // 1. Detect Category Spikes
            detectCategorySpikes(user, candidates, currentYm);

            // 2. Detect Budget Warnings and Exceeded Limits
            detectBudgetIssues(user, candidates, currentYm);

            // 3. Detect Savings Rate Deterioration
            detectSavingsDeterioration(user, candidates, monthStart, today, currentYm);

            // 4. Detect Large Transaction Anomalies
            detectLargeTransactionAnomalies(user, candidates, monthStart, today, currentYm);

            // 5. Detect Goals At Risk
            detectGoalsAtRisk(user, candidates, currentYm);
        } catch (Exception e) {
            log.error("Error during proactive insight detection for user {}: {}", user.getUserId(), e.getMessage(), e);
        }

        return candidates;
    }

    private void detectCategorySpikes(
            SpendSenseUserPrincipal user,
            List<ProactiveInsightCandidate> candidates,
            YearMonth currentYm
    ) {
        FinancialProfile profile = financialContextService.computeFinancialProfile(user, 6);
        if (profile == null || profile.categories().isEmpty()) {
            return;
        }

        for (CategoryProfile cp : profile.categories()) {
            BigDecimal recent = cp.recentMonthlySpend();
            BigDecimal average = cp.averageMonthlySpend();

            if (recent != null && average != null && average.compareTo(new BigDecimal("500.00")) > 0) {
                BigDecimal diff = recent.subtract(average);
                if (diff.compareTo(new BigDecimal("1000.00")) >= 0) {
                    BigDecimal ratio = recent.divide(average, 2, RoundingMode.HALF_UP);
                    if (ratio.compareTo(new BigDecimal("1.25")) >= 0) {
                        BigDecimal pctIncrease = ratio.subtract(BigDecimal.ONE)
                                .multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP);

                        Map<String, Object> facts = new LinkedHashMap<>();
                        facts.put("category", cp.category().name());
                        facts.put("currentAmount", recent);
                        facts.put("historicalAverage", average);
                        facts.put("percentageChange", pctIncrease);

                        String title = String.format("%s spending is up %s%% this month", cp.category(), pctIncrease);
                        String summary = String.format("Your %s spending is running about %s%% (₹%s) above your recent historical monthly average of ₹%s.",
                                cp.category(), pctIncrease, diff.setScale(0, RoundingMode.HALF_UP), average.setScale(0, RoundingMode.HALF_UP));
                        String dedupKey = String.format("CATEGORY_SPIKE:%s:%s", cp.category().name(), currentYm);

                        candidates.add(new ProactiveInsightCandidate(
                                ProactiveInsightType.CATEGORY_SPIKE,
                                InsightSeverity.WARNING,
                                title,
                                summary,
                                facts,
                                "Consider reviewing discretionary expenses in " + cp.category() + " before month end.",
                                dedupKey,
                                70.0 + pctIncrease.doubleValue()
                        ));
                    }
                }
            }
        }
    }

    private void detectBudgetIssues(
            SpendSenseUserPrincipal user,
            List<ProactiveInsightCandidate> candidates,
            YearMonth currentYm
    ) {
        try {
            BudgetSummaryResponse budgetSummary = budgetService.getSummary(currentYm.toString(), null, null);
            if (budgetSummary == null || budgetSummary.budgets().isEmpty()) {
                return;
            }

            for (BudgetResponse b : budgetSummary.budgets()) {
                double pctUsed = b.percentageUsed();
                if (pctUsed >= 100.0) {
                    BigDecimal overAmount = b.spentAmount().subtract(b.limitAmount());
                    Map<String, Object> facts = new LinkedHashMap<>();
                    facts.put("category", b.category().name());
                    facts.put("limit", b.limitAmount());
                    facts.put("spent", b.spentAmount());
                    facts.put("percentageUsed", pctUsed);

                    String title = String.format("%s budget limit exceeded", b.category());
                    String summary = String.format("You have exceeded your %s budget of ₹%s by ₹%s (%.0f%% spent).",
                            b.category(), b.limitAmount().setScale(0, RoundingMode.HALF_UP),
                            overAmount.setScale(0, RoundingMode.HALF_UP), pctUsed);
                    String dedupKey = String.format("BUDGET_EXCEEDED:%s:%s", b.category().name(), currentYm);

                    candidates.add(new ProactiveInsightCandidate(
                            ProactiveInsightType.BUDGET_EXCEEDED,
                            InsightSeverity.CRITICAL,
                            title,
                            summary,
                            facts,
                            "Pause further non-essential spending in " + b.category() + " or adjust your budget.",
                            dedupKey,
                            95.0 + (pctUsed - 100.0)
                    ));
                } else if (pctUsed >= 85.0) {
                    BigDecimal remaining = b.remainingAmount();
                    Map<String, Object> facts = new LinkedHashMap<>();
                    facts.put("category", b.category().name());
                    facts.put("limit", b.limitAmount());
                    facts.put("spent", b.spentAmount());
                    facts.put("remaining", remaining);
                    facts.put("percentageUsed", pctUsed);

                    String title = String.format("%s budget approaching limit (%.0f%% used)", b.category(), pctUsed);
                    String summary = String.format("You have used %.0f%% of your %s budget with ₹%s remaining for the rest of %s.",
                            pctUsed, b.category(), remaining.setScale(0, RoundingMode.HALF_UP), currentYm.getMonth().name());
                    String dedupKey = String.format("BUDGET_WARNING:%s:%s", b.category().name(), currentYm);

                    candidates.add(new ProactiveInsightCandidate(
                            ProactiveInsightType.BUDGET_WARNING,
                            InsightSeverity.WARNING,
                            title,
                            summary,
                            facts,
                            "Pace remaining spending in " + b.category() + " to stay within your guardrail.",
                            dedupKey,
                            80.0 + pctUsed
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Could not check budgets for proactive insights: {}", e.getMessage());
        }
    }

    private void detectSavingsDeterioration(
            SpendSenseUserPrincipal user,
            List<ProactiveInsightCandidate> candidates,
            LocalDate monthStart,
            LocalDate today,
            YearMonth currentYm
    ) {
        try {
            PeriodComparisonResponse comparison = analyticsService.getComparison(monthStart, today);
            if (comparison == null || comparison.currentSavingsRate() == null || comparison.previousSavingsRate() == null) {
                return;
            }

            BigDecimal currentRate = comparison.currentSavingsRate();
            BigDecimal prevRate = comparison.previousSavingsRate();

            // Deterioration by 15+ percentage points
            if (prevRate.subtract(currentRate).compareTo(new BigDecimal("15.0")) >= 0
                    && comparison.income() != null && comparison.income().currentAmount().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal drop = prevRate.subtract(currentRate);
                Map<String, Object> facts = new LinkedHashMap<>();
                facts.put("currentSavingsRate", currentRate);
                facts.put("previousSavingsRate", prevRate);
                facts.put("rateDropPercentagePoints", drop);

                String title = String.format("Savings rate down by %s percentage points", drop.setScale(0, RoundingMode.HALF_UP));
                String summary = String.format("Your current savings rate is running at %s%%, down from %s%% in the previous period.",
                        currentRate.setScale(1, RoundingMode.HALF_UP), prevRate.setScale(1, RoundingMode.HALF_UP));
                String dedupKey = String.format("SAVINGS_RATE_DETERIORATION:%s", currentYm);

                candidates.add(new ProactiveInsightCandidate(
                        ProactiveInsightType.SAVINGS_RATE_DETERIORATION,
                        InsightSeverity.WARNING,
                        title,
                        summary,
                        facts,
                        "Review recent major expenses to realign with your monthly savings target.",
                        dedupKey,
                        75.0 + drop.doubleValue()
                ));
            }
        } catch (Exception e) {
            log.warn("Could not check savings rate comparison: {}", e.getMessage());
        }
    }

    private void detectLargeTransactionAnomalies(
            SpendSenseUserPrincipal user,
            List<ProactiveInsightCandidate> candidates,
            LocalDate monthStart,
            LocalDate today,
            YearMonth currentYm
    ) {
        try {
            List<SpendingAnomaly> anomalies = anomalyDetectionService.detectAnomalies(monthStart, today);
            for (SpendingAnomaly anomaly : anomalies) {
                if (anomaly.type() == AnomalyType.LARGE_TRANSACTION && anomaly.amount().compareTo(new BigDecimal("5000.00")) >= 0) {
                    Map<String, Object> facts = new LinkedHashMap<>();
                    facts.put("amount", anomaly.amount());
                    facts.put("title", anomaly.title());
                    facts.put("date", anomaly.date());

                    String title = "Unusually high expense detected: ₹" + anomaly.amount().setScale(0, RoundingMode.HALF_UP);
                    String summary = String.format("A transaction of ₹%s on %s is significantly above your typical transaction size (%s).",
                            anomaly.amount().setScale(0, RoundingMode.HALF_UP), anomaly.date(), anomaly.reason());
                    String dedupKey = String.format("LARGE_TX:%s:%s", anomaly.id(), anomaly.date());

                    candidates.add(new ProactiveInsightCandidate(
                            ProactiveInsightType.LARGE_TRANSACTION_ANOMALY,
                            InsightSeverity.WARNING,
                            title,
                            summary,
                            facts,
                            "Confirm this transaction was expected and categorize appropriately.",
                            dedupKey,
                            85.0
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Could not check anomalies for proactive insights: {}", e.getMessage());
        }
    }

    private void detectGoalsAtRisk(
            SpendSenseUserPrincipal user,
            List<ProactiveInsightCandidate> candidates,
            YearMonth currentYm
    ) {
        try {
            List<GoalResponse> goals = goalService.list();
            LocalDate today = LocalDate.now();

            for (GoalResponse g : goals) {
                if (g.targetDate() != null && g.targetDate().isAfter(today)) {
                    long monthsLeft = ChronoUnit.MONTHS.between(today.withDayOfMonth(1), g.targetDate().withDayOfMonth(1));
                    if (monthsLeft > 0 && monthsLeft <= 6) {
                        BigDecimal remaining = g.targetAmount().subtract(g.currentAmount());
                        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal requiredMonthly = remaining.divide(BigDecimal.valueOf(monthsLeft), 2, RoundingMode.HALF_UP);
                            // If remaining is more than 60% of target with under 3 months left
                            double progressPct = g.currentAmount().multiply(BigDecimal.valueOf(100))
                                    .divide(g.targetAmount(), 2, RoundingMode.HALF_UP).doubleValue();

                            if (monthsLeft <= 3 && progressPct < 50.0) {
                                Map<String, Object> facts = new LinkedHashMap<>();
                                facts.put("goalTitle", g.name());
                                facts.put("targetAmount", g.targetAmount());
                                facts.put("currentAmount", g.currentAmount());
                                facts.put("monthsRemaining", monthsLeft);
                                facts.put("requiredMonthly", requiredMonthly);

                                String title = String.format("Goal '%s' is at risk of missing target date", g.name());
                                String summary = String.format("Your '%s' goal has ₹%s remaining with only %d months left. You will need ₹%s/month to meet your target.",
                                        g.name(), remaining.setScale(0, RoundingMode.HALF_UP), monthsLeft, requiredMonthly.setScale(0, RoundingMode.HALF_UP));
                                String dedupKey = String.format("GOAL_AT_RISK:%s:%s", g.id(), currentYm);

                                candidates.add(new ProactiveInsightCandidate(
                                        ProactiveInsightType.GOAL_AT_RISK,
                                        InsightSeverity.WARNING,
                                        title,
                                        summary,
                                        facts,
                                        "Consider increasing monthly allocations or extending your target date.",
                                        dedupKey,
                                        80.0
                                ));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not check goals for proactive insights: {}", e.getMessage());
        }
    }
}
