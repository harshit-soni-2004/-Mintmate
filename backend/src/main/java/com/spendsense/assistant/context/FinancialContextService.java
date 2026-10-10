package com.spendsense.assistant.context;

import com.spendsense.analytics.AggregationPeriod;
import com.spendsense.analytics.AnalyticsService;
import com.spendsense.analytics.TrendDirection;
import com.spendsense.analytics.dto.*;
import com.spendsense.assistant.context.model.*;
import com.spendsense.assistant.dto.*;
import com.spendsense.budget.BudgetService;
import com.spendsense.budget.dto.BudgetResponse;
import com.spendsense.goal.GoalService;
import com.spendsense.goal.dto.GoalResponse;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FinancialContextService {

    private static final Logger log = LoggerFactory.getLogger(FinancialContextService.class);

    private final AnalyticsService analyticsService;
    private final BudgetService budgetService;
    private final GoalService goalService;
    private final TransactionRepository transactionRepository;

    public FinancialContextService(
            AnalyticsService analyticsService,
            BudgetService budgetService,
            GoalService goalService,
            TransactionRepository transactionRepository
    ) {
        this.analyticsService = analyticsService;
        this.budgetService = budgetService;
        this.goalService = goalService;
        this.transactionRepository = transactionRepository;
    }

    public FinancialProfile computeFinancialProfile(SpendSenseUserPrincipal user, int lookbackMonths) {
        int months = Math.max(1, Math.min(lookbackMonths, 24));
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(months).withDayOfMonth(1);

        UUID userId = user.getUserId();
        long txExpenseCount = transactionRepository.countByUserIdAndTypeAndDateBetween(userId, TransactionType.EXPENSE, from, to);
        long txIncomeCount = transactionRepository.countByUserIdAndTypeAndDateBetween(userId, TransactionType.INCOME, from, to);
        long totalCount = txExpenseCount + txIncomeCount;

        // 1. Data Sufficiency
        DataSufficiency sufficiencyLevel;
        String sufficiencyExplanation;
        if (months < 3 || totalCount < 8) {
            sufficiencyLevel = DataSufficiency.LOW;
            sufficiencyExplanation = String.format("Limited history (%d months, %d transactions). Personalized estimates are preliminary baselines.", months, totalCount);
        } else if (months >= 6 && totalCount >= 25 && txIncomeCount >= 3) {
            sufficiencyLevel = DataSufficiency.HIGH;
            sufficiencyExplanation = String.format("Strong data sufficiency with %d months of continuous activity across %d transactions.", months, totalCount);
        } else {
            sufficiencyLevel = DataSufficiency.MEDIUM;
            sufficiencyExplanation = String.format("Moderate data sufficiency across %d months and %d transactions.", months, totalCount);
        }
        DataSufficiencyProfile sufficiency = new DataSufficiencyProfile(
                sufficiencyLevel,
                months,
                totalCount,
                txIncomeCount > 0,
                txExpenseCount > 0,
                sufficiencyExplanation
        );

        // 2. Spending Trends & Income Aggregation
        SpendingTrendResponse trendResponse;
        try {
            trendResponse = analyticsService.getTrends(AggregationPeriod.MONTHLY, from, to);
        } catch (Exception e) {
            log.warn("Could not retrieve monthly trends for user {}: {}", userId, e.getMessage());
            trendResponse = null;
        }
        if (trendResponse == null) {
            trendResponse = new SpendingTrendResponse(AggregationPeriod.MONTHLY, Collections.emptyList(), from, to);
        }

        List<SpendingTrendPoint> trendPoints = trendResponse.points() != null ? trendResponse.points() : Collections.emptyList();

        List<BigDecimal> monthlyIncomes = trendPoints.stream()
                .map(SpendingTrendPoint::totalIncome)
                .filter(i -> i != null && i.compareTo(BigDecimal.ZERO) > 0)
                .toList();

        List<BigDecimal> monthlyExpenses = trendPoints.stream()
                .map(SpendingTrendPoint::totalExpense)
                .filter(e -> e != null && e.compareTo(BigDecimal.ZERO) > 0)
                .toList();

        BigDecimal avgIncome = calculateAverage(monthlyIncomes);
        BigDecimal medianIncome = calculateMedian(monthlyIncomes);
        BigDecimal recentIncome = monthlyIncomes.isEmpty() ? BigDecimal.ZERO : monthlyIncomes.getLast();

        TrendDirection incomeTrend = determineTrend(monthlyIncomes);
        String incomeStability = calculateStability(monthlyIncomes, avgIncome);

        IncomeProfile incomeProfile = new IncomeProfile(
                avgIncome,
                medianIncome,
                recentIncome,
                incomeTrend,
                incomeStability,
                months,
                sufficiencyLevel
        );

        // 3. Spending Profile
        BigDecimal avgExpenses = calculateAverage(monthlyExpenses);
        BigDecimal medianExpenses = calculateMedian(monthlyExpenses);
        BigDecimal recentExpenses = monthlyExpenses.isEmpty() ? BigDecimal.ZERO : monthlyExpenses.getLast();
        TrendDirection spendingTrend = determineTrend(monthlyExpenses);
        String spendingVolatility = calculateStability(monthlyExpenses, avgExpenses);
        BigDecimal avgMonthlySavings = avgIncome.subtract(avgExpenses).max(BigDecimal.ZERO);

        // 4. Category Profiles
        CategoryBreakdownResponse breakdown;
        try {
            breakdown = analyticsService.getCategoryBreakdown(from, to);
        } catch (Exception e) {
            log.warn("Could not retrieve category breakdown: {}", e.getMessage());
            breakdown = null;
        }

        List<CategoryProfile> categoryProfiles = new ArrayList<>();
        List<String> topSpendingCategories = new ArrayList<>();

        if (breakdown != null && breakdown.categories() != null) {
            for (CategoryAnalysis ca : breakdown.categories()) {
                BigDecimal catMonthlyAvg = ca.amount().divide(BigDecimal.valueOf(Math.max(1, months)), 2, RoundingMode.HALF_UP);
                BigDecimal catRecent = ca.amount(); // Current breakdown slice represents latest activity

                String behavioralFact = String.format(
                        "%s spending averaged ₹%.2f per month across %d months (%.1f%% of total expenses).",
                        ca.category().getDisplayName(),
                        catMonthlyAvg,
                        months,
                        ca.percentage()
                );

                CategoryProfile cp = new CategoryProfile(
                        ca.category(),
                        ca.category().getDisplayName(),
                        catMonthlyAvg,
                        catRecent,
                        catMonthlyAvg.multiply(new BigDecimal("0.70")).setScale(2, RoundingMode.HALF_UP),
                        catMonthlyAvg.multiply(new BigDecimal("1.30")).setScale(2, RoundingMode.HALF_UP),
                        ca.percentage(),
                        ca.direction() != null ? ca.direction() : TrendDirection.UNCHANGED,
                        ca.percentageChange() != null && ca.percentageChange().abs().compareTo(new BigDecimal("25")) > 0 ? "HIGH" : "MEDIUM",
                        months,
                        sufficiencyLevel,
                        behavioralFact
                );
                categoryProfiles.add(cp);
                topSpendingCategories.add(ca.category().getDisplayName());
            }
        }

        // 5. Recurring Commitments
        RecurringCommitmentProfile recurringProfile = computeRecurringCommitments(userId, from, to, sufficiencyLevel);

        // 6. Savings Profile & Goals
        List<GoalResponse> activeGoals;
        try {
            activeGoals = goalService.list();
        } catch (Exception e) {
            activeGoals = Collections.emptyList();
        }
        if (activeGoals == null) activeGoals = Collections.emptyList();

        BigDecimal totalGoalTarget = BigDecimal.ZERO;
        BigDecimal totalGoalProgress = BigDecimal.ZERO;
        BigDecimal requiredMonthlyGoalSavings = BigDecimal.ZERO;

        for (GoalResponse g : activeGoals) {
            totalGoalTarget = totalGoalTarget.add(g.targetAmount() != null ? g.targetAmount() : BigDecimal.ZERO);
            totalGoalProgress = totalGoalProgress.add(g.currentAmount() != null ? g.currentAmount() : BigDecimal.ZERO);
            BigDecimal remaining = g.remainingAmount() != null ? g.remainingAmount() : BigDecimal.ZERO;
            long monthsLeft = 12;
            if (g.targetDate() != null) {
                monthsLeft = Math.max(1, ChronoUnit.MONTHS.between(LocalDate.now(), g.targetDate()));
            }
            if (monthsLeft > 0 && remaining.compareTo(BigDecimal.ZERO) > 0) {
                requiredMonthlyGoalSavings = requiredMonthlyGoalSavings.add(remaining.divide(BigDecimal.valueOf(monthsLeft), 2, RoundingMode.HALF_UP));
            }
        }
        BigDecimal totalGoalRemaining = totalGoalTarget.subtract(totalGoalProgress).max(BigDecimal.ZERO);

        BigDecimal avgSavingsRate = avgIncome.compareTo(BigDecimal.ZERO) > 0
                ? avgMonthlySavings.multiply(new BigDecimal("100")).divide(avgIncome, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal recentSavings = recentIncome.subtract(recentExpenses);
        BigDecimal recentSavingsRate = recentIncome.compareTo(BigDecimal.ZERO) > 0
                ? recentSavings.multiply(new BigDecimal("100")).divide(recentIncome, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal monthlyGoalSavingsGap = requiredMonthlyGoalSavings.subtract(avgMonthlySavings).max(BigDecimal.ZERO);
        boolean onTrackForGoals = avgMonthlySavings.compareTo(requiredMonthlyGoalSavings) >= 0;

        SavingsProfile savingsProfile = new SavingsProfile(
                avgMonthlySavings,
                recentSavings,
                avgSavingsRate,
                recentSavingsRate,
                recentSavings.compareTo(avgMonthlySavings) >= 0 ? TrendDirection.INCREASED : TrendDirection.DECREASED,
                totalGoalTarget,
                totalGoalProgress,
                totalGoalRemaining,
                requiredMonthlyGoalSavings,
                monthlyGoalSavingsGap,
                onTrackForGoals,
                sufficiencyLevel
        );

        // 7. Spending Flexibility Profile
        BigDecimal committed = recurringProfile.totalMonthlyCommitted();
        BigDecimal baselineExpenses = avgExpenses.subtract(committed).max(BigDecimal.ZERO);
        BigDecimal availableFlexibility = avgIncome.subtract(committed).subtract(baselineExpenses).max(BigDecimal.ZERO);

        SpendingFlexibilityProfile flexibilityProfile = new SpendingFlexibilityProfile(
                avgIncome,
                committed,
                baselineExpenses,
                availableFlexibility,
                "Estimated monthly spending flexibility = Average Monthly Income minus Recurring Commitments minus Baseline Living Expenses.",
                sufficiencyLevel
        );

        // 8. Active Budgets
        List<BudgetResponse> activeBudgets;
        try {
            activeBudgets = budgetService.list(null, null, null);
        } catch (Exception e) {
            activeBudgets = Collections.emptyList();
        }
        if (activeBudgets == null) activeBudgets = Collections.emptyList();

        SpendingProfile spendingProfile = new SpendingProfile(
                avgExpenses,
                medianExpenses,
                recentExpenses,
                spendingTrend,
                spendingVolatility,
                avgMonthlySavings,
                topSpendingCategories,
                committed,
                availableFlexibility,
                sufficiencyLevel
        );

        return new FinancialProfile(
                LocalDate.now(),
                months,
                sufficiency,
                incomeProfile,
                spendingProfile,
                savingsProfile,
                categoryProfiles,
                recurringProfile,
                flexibilityProfile,
                activeBudgets,
                activeGoals
        );
    }

    public BudgetRecommendationResponse calculateBudgetRecommendation(SpendSenseUserPrincipal user, TransactionCategory category, int lookbackMonths) {
        FinancialProfile profile = computeFinancialProfile(user, lookbackMonths);
        DataSufficiency sufficiency = profile.dataSufficiency().level();

        Optional<CategoryProfile> matchedCat = profile.categories().stream()
                .filter(c -> c.category() == category)
                .findFirst();

        Optional<BudgetResponse> existingBudget = profile.activeBudgets().stream()
                .filter(b -> b.category() == category)
                .findFirst();

        BigDecimal existingLimit = existingBudget.map(BudgetResponse::limitAmount).orElse(null);

        List<String> supportingFacts = new ArrayList<>();
        List<String> assumptions = new ArrayList<>();

        BigDecimal recommendedBudget;
        BigDecimal rangeMin;
        BigDecimal rangeMax;
        BigDecimal histAvg = BigDecimal.ZERO;
        BigDecimal recentAvg = BigDecimal.ZERO;
        String confidence;

        if (matchedCat.isPresent()) {
            CategoryProfile cp = matchedCat.get();
            histAvg = cp.averageMonthlySpend();
            recentAvg = cp.recentMonthlySpend();

            // Deterministic weighted formula: 60% recent average + 40% historical baseline
            BigDecimal weighted = recentAvg.multiply(new BigDecimal("0.60")).add(histAvg.multiply(new BigDecimal("0.40")));
            recommendedBudget = roundToNearest(weighted, 100);

            BigDecimal lowerBound = histAvg.min(recentAvg).multiply(new BigDecimal("0.90"));
            BigDecimal upperBound = histAvg.max(recentAvg).multiply(new BigDecimal("1.10"));
            rangeMin = roundToNearest(lowerBound, 50);
            rangeMax = roundToNearest(upperBound, 50);

            confidence = sufficiency == DataSufficiency.HIGH ? "HIGH" : (sufficiency == DataSufficiency.MEDIUM ? "MEDIUM" : "LOW");

            supportingFacts.add(String.format("Historical average monthly spend on %s is ₹%.2f over %d months.", cp.displayName(), histAvg, profile.historyMonths()));
            supportingFacts.add(String.format("Recent spend on %s is ₹%.2f (accounting for %.1f%% of total monthly spending).", cp.displayName(), recentAvg, cp.percentageOfExpenses()));
            if (existingLimit != null) {
                supportingFacts.add(String.format("Existing active budget limit for %s is ₹%.2f.", cp.displayName(), existingLimit));
            }

            assumptions.add("Recommendation is calibrated to your demonstrated spending history without requiring abrupt lifestyle changes.");
            assumptions.add(String.format("Assumes consistent monthly income near your historical average of ₹%.2f.", profile.income().averageMonthlyIncome()));
        } else {
            // New or zero-spend category
            recommendedBudget = new BigDecimal("3000.00");
            rangeMin = new BigDecimal("2000.00");
            rangeMax = new BigDecimal("4000.00");
            confidence = "LOW";

            supportingFacts.add(String.format("No previous transaction activity found for %s in the last %d months.", category.getDisplayName(), profile.historyMonths()));
            assumptions.add("Preliminary starting recommendation pending initial spending data.");
        }

        return new BudgetRecommendationResponse(
                category,
                recommendedBudget,
                rangeMin,
                rangeMax,
                confidence,
                sufficiency,
                profile.historyMonths(),
                histAvg,
                recentAvg,
                existingLimit,
                supportingFacts,
                assumptions
        );
    }

    public SavingsTargetRecommendationResponse calculateSavingsRecommendation(SpendSenseUserPrincipal user, int lookbackMonths) {
        FinancialProfile profile = computeFinancialProfile(user, lookbackMonths);
        DataSufficiency sufficiency = profile.dataSufficiency().level();

        BigDecimal income = profile.income().averageMonthlyIncome();
        BigDecimal expenses = profile.spending().averageMonthlyExpenses();
        BigDecimal committed = profile.recurring().totalMonthlyCommitted();
        BigDecimal goalNeed = profile.savings().requiredMonthlyGoalSavings();
        BigDecimal historicalSavings = profile.savings().averageMonthlySavings();

        List<String> facts = new ArrayList<>();
        List<String> assumptions = new ArrayList<>();

        facts.add(String.format("Average monthly income is ₹%.2f.", income));
        facts.add(String.format("Average monthly living expenses are ₹%.2f.", expenses));
        if (committed.compareTo(BigDecimal.ZERO) > 0) {
            facts.add(String.format("Fixed monthly recurring commitments total ₹%.2f.", committed));
        }

        BigDecimal recommendedSavings;
        BigDecimal recommendedRate;
        String confidence;

        if (sufficiency == DataSufficiency.LOW) {
            // Conservative 10-15% target
            recommendedSavings = income.multiply(new BigDecimal("0.10")).setScale(0, RoundingMode.HALF_UP);
            if (recommendedSavings.compareTo(BigDecimal.ZERO) == 0) {
                recommendedSavings = new BigDecimal("5000.00");
            }
            recommendedRate = new BigDecimal("10.0");
            confidence = "LOW";
            assumptions.add("Limited historical data; 10% baseline recommended as an achievable starting habit.");
        } else {
            // Sustainable deterministic formula
            if (goalNeed.compareTo(BigDecimal.ZERO) > 0) {
                facts.add(String.format("Active savings goals require ₹%.2f per month to complete on schedule.", goalNeed));
                recommendedSavings = goalNeed.max(historicalSavings).min(income.subtract(committed).multiply(new BigDecimal("0.80")));
            } else {
                // 20% rule of thumb adapted to historical rate
                BigDecimal targetRate = profile.savings().averageSavingsRate().max(new BigDecimal("20.0")).min(new BigDecimal("50.0"));
                recommendedSavings = income.multiply(targetRate).divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP);
            }

            recommendedSavings = roundToNearest(recommendedSavings, 500);
            recommendedRate = income.compareTo(BigDecimal.ZERO) > 0
                    ? recommendedSavings.multiply(new BigDecimal("100")).divide(income, 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            confidence = sufficiency == DataSufficiency.HIGH ? "HIGH" : "MEDIUM";

            assumptions.add("Target is grounded in your verified cashflow and existing goal deadlines.");
            assumptions.add("Leaves an estimated buffer for discretionary and unexpected spending.");
        }

        BigDecimal rangeMin = roundToNearest(recommendedSavings.multiply(new BigDecimal("0.85")), 100);
        BigDecimal rangeMax = roundToNearest(recommendedSavings.multiply(new BigDecimal("1.15")), 100);

        return new SavingsTargetRecommendationResponse(
                recommendedSavings,
                rangeMin,
                rangeMax,
                recommendedRate,
                confidence,
                sufficiency,
                profile.historyMonths(),
                income,
                expenses,
                committed,
                goalNeed,
                facts,
                assumptions
        );
    }

    public SpendingFlexibilityResponse getSpendingFlexibility(SpendSenseUserPrincipal user, int lookbackMonths) {
        FinancialProfile profile = computeFinancialProfile(user, lookbackMonths);
        BigDecimal income = profile.income().averageMonthlyIncome();
        BigDecimal committed = profile.recurring().totalMonthlyCommitted();
        BigDecimal baselineLiving = profile.flexibility().typicalLivingExpenses();
        BigDecimal discretionary = profile.flexibility().availableDiscretionaryCapacity();

        BigDecimal committedPct = income.compareTo(BigDecimal.ZERO) > 0
                ? committed.multiply(new BigDecimal("100")).divide(income, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<String> items = new ArrayList<>();
        items.add(String.format("Total monthly income: ₹%.2f", income));
        items.add(String.format("Committed recurring obligations: ₹%.2f (%.1f%% of income)", committed, committedPct));
        items.add(String.format("Baseline non-recurring living expenses: ₹%.2f", baselineLiving));
        items.add(String.format("Available discretionary flexibility: ₹%.2f", discretionary));

        return new SpendingFlexibilityResponse(
                income,
                committed,
                baselineLiving,
                discretionary,
                committedPct,
                profile.dataSufficiency().level().name(),
                profile.dataSufficiency().level(),
                "Discretionary Flexibility = Monthly Income - Recurring Commitments - Baseline Living Expenses",
                items
        );
    }

    public CategoryInsightsResponse getCategoryInsights(SpendSenseUserPrincipal user, int lookbackMonths) {
        FinancialProfile profile = computeFinancialProfile(user, lookbackMonths);
        List<CategoryProfile> cats = profile.categories();

        String highestSpend = "N/A";
        String mostVolatile = "N/A";
        String fastestGrowing = "N/A";

        if (!cats.isEmpty()) {
            highestSpend = cats.stream()
                    .max(Comparator.comparing(CategoryProfile::averageMonthlySpend))
                    .map(CategoryProfile::displayName)
                    .orElse("N/A");

            mostVolatile = cats.stream()
                    .filter(c -> "HIGH".equalsIgnoreCase(c.volatility()))
                    .findFirst()
                    .map(CategoryProfile::displayName)
                    .orElse(cats.getFirst().displayName());

            fastestGrowing = cats.stream()
                    .filter(c -> c.trend() == TrendDirection.INCREASED)
                    .findFirst()
                    .map(CategoryProfile::displayName)
                    .orElse(highestSpend);
        }

        List<String> insights = new ArrayList<>();
        if (!cats.isEmpty()) {
            insights.add(String.format("Highest spend category: %s", highestSpend));
            insights.add(String.format("Most volatile category: %s", mostVolatile));
            insights.add(String.format("Fastest growing category: %s", fastestGrowing));
            for (CategoryProfile cp : cats.stream().limit(3).toList()) {
                insights.add(cp.behavioralSummary());
            }
        } else {
            insights.add("Insufficient category breakdown history available.");
        }

        return new CategoryInsightsResponse(
                highestSpend,
                mostVolatile,
                fastestGrowing,
                cats,
                insights,
                profile.dataSufficiency().level()
        );
    }

    private RecurringCommitmentProfile computeRecurringCommitments(UUID userId, LocalDate from, LocalDate to, DataSufficiency sufficiency) {
        List<Transaction> transactions = transactionRepository.findByUser_IdAndTransactionTypeAndTransactionDateBetweenOrderByAmountDesc(
                userId, TransactionType.EXPENSE, from, to);

        Map<String, List<Transaction>> groups = transactions.stream()
                .filter(t -> t.getMerchant() != null && !t.getMerchant().isBlank())
                .collect(Collectors.groupingBy(t -> t.getMerchant().trim().toLowerCase(Locale.ROOT)));

        List<RecurringExpenseItem> items = new ArrayList<>();
        BigDecimal totalCommitted = BigDecimal.ZERO;

        for (Map.Entry<String, List<Transaction>> entry : groups.entrySet()) {
            List<Transaction> txs = entry.getValue();
            if (txs.size() < 2) continue;

            txs.sort(Comparator.comparing(Transaction::getTransactionDate));
            BigDecimal amt = txs.getFirst().getAmount();

            String confidence = txs.size() >= 3 ? "HIGH" : "MEDIUM";
            totalCommitted = totalCommitted.add(amt);

            items.add(new RecurringExpenseItem(
                    txs.getFirst().getMerchant(),
                    txs.getFirst().getCategory(),
                    amt,
                    amt,
                    "MONTHLY",
                    confidence,
                    txs.size(),
                    txs.getLast().getTransactionDate()
            ));
        }

        return new RecurringCommitmentProfile(
                totalCommitted,
                items.size(),
                items,
                sufficiency
        );
    }

    private BigDecimal calculateAverage(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateMedian(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) return BigDecimal.ZERO;
        List<BigDecimal> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(mid);
        } else {
            return sorted.get(mid - 1).add(sorted.get(mid)).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
        }
    }

    private TrendDirection determineTrend(List<BigDecimal> values) {
        if (values.size() < 2) return TrendDirection.UNCHANGED;
        BigDecimal first = values.getFirst();
        BigDecimal last = values.getLast();
        BigDecimal diff = last.subtract(first);
        if (diff.compareTo(new BigDecimal("500")) > 0) return TrendDirection.INCREASED;
        if (diff.compareTo(new BigDecimal("-500")) < 0) return TrendDirection.DECREASED;
        return TrendDirection.UNCHANGED;
    }

    private String calculateStability(List<BigDecimal> values, BigDecimal avg) {
        if (values.size() < 2 || avg.compareTo(BigDecimal.ZERO) == 0) return "MEDIUM";
        BigDecimal varianceSum = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            BigDecimal diff = v.subtract(avg);
            varianceSum = varianceSum.add(diff.multiply(diff));
        }
        double variance = varianceSum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP).doubleValue();
        double stdDev = Math.sqrt(variance);
        double cv = stdDev / avg.doubleValue();
        if (cv < 0.15) return "HIGH";
        if (cv < 0.35) return "MEDIUM";
        return "LOW";
    }

    private BigDecimal roundToNearest(BigDecimal value, int unit) {
        if (value == null) return BigDecimal.ZERO;
        BigDecimal u = BigDecimal.valueOf(unit);
        return value.divide(u, 0, RoundingMode.HALF_UP).multiply(u).setScale(2, RoundingMode.HALF_UP);
    }
}
