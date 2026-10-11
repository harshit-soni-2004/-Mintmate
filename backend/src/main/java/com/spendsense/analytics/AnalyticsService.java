package com.spendsense.analytics;

import com.spendsense.analytics.dto.CategoryAnalysis;
import com.spendsense.analytics.dto.CategoryBreakdownResponse;
import com.spendsense.analytics.dto.PeriodComparisonResponse;
import com.spendsense.analytics.dto.SpendingSummaryResponse;
import com.spendsense.analytics.dto.SpendingTrendPoint;
import com.spendsense.analytics.dto.SpendingTrendResponse;
import com.spendsense.analytics.dto.TrendMetric;
import com.spendsense.dashboard.dto.CategorySpend;
import com.spendsense.dashboard.dto.DailySpend;
import com.spendsense.security.CurrentUser;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

    private final TransactionRepository transactionRepository;
    private final CurrentUser currentUser;

    public AnalyticsService(TransactionRepository transactionRepository, CurrentUser currentUser) {
        this.transactionRepository = transactionRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public SpendingSummaryResponse getSummary(LocalDate fromDate, LocalDate toDate) {
        UUID userId = currentUser.require().getUserId();
        LocalDate[] range = normalizeRange(fromDate, toDate);
        LocalDate from = range[0];
        LocalDate to = range[1];

        BigDecimal totalIncome = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                userId, TransactionType.INCOME, from, to);
        if (totalIncome == null) totalIncome = BigDecimal.ZERO;

        BigDecimal totalExpenses = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                userId, TransactionType.EXPENSE, from, to);
        if (totalExpenses == null) totalExpenses = BigDecimal.ZERO;

        BigDecimal netSavings = totalIncome.subtract(totalExpenses);

        BigDecimal savingsRate = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            savingsRate = netSavings.max(BigDecimal.ZERO)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(totalIncome, 2, RoundingMode.HALF_UP);
        }

        long expenseCount = transactionRepository.countByUserIdAndTypeAndDateBetween(
                userId, TransactionType.EXPENSE, from, to);
        long incomeCount = transactionRepository.countByUserIdAndTypeAndDateBetween(
                userId, TransactionType.INCOME, from, to);
        long totalCount = expenseCount + incomeCount;

        BigDecimal averageExpense = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (expenseCount > 0) {
            averageExpense = totalExpenses.divide(BigDecimal.valueOf(expenseCount), 2, RoundingMode.HALF_UP);
        }

        BigDecimal largestExpense = transactionRepository.maxAmountByUserIdAndTypeAndDateBetween(
                userId, TransactionType.EXPENSE, from, to);
        if (largestExpense == null) {
            largestExpense = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal totalTransfers = transactionRepository.sumAmountByUserIdAndTypeAndDirectionAndDateBetween(
                userId, TransactionType.TRANSFER, com.spendsense.transaction.TransferDirection.OUT, from, to);
        if (totalTransfers == null) totalTransfers = BigDecimal.ZERO;

        BigDecimal investmentContributions = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                userId, TransactionType.INVESTMENT, from, to);
        if (investmentContributions == null) investmentContributions = BigDecimal.ZERO;

        return new SpendingSummaryResponse(
                totalIncome,
                totalExpenses,
                netSavings,
                savingsRate,
                totalCount,
                averageExpense,
                largestExpense,
                from,
                to,
                totalTransfers,
                investmentContributions
        );
    }

    @Transactional(readOnly = true)
    public PeriodComparisonResponse getComparison(LocalDate fromDate, LocalDate toDate) {
        UUID userId = currentUser.require().getUserId();
        LocalDate[] curRange = normalizeRange(fromDate, toDate);
        LocalDate[] prevRange = resolvePreviousPeriod(curRange[0], curRange[1]);

        BigDecimal curIncome = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                userId, TransactionType.INCOME, curRange[0], curRange[1]);
        if (curIncome == null) curIncome = BigDecimal.ZERO;
        BigDecimal curExpenses = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                userId, TransactionType.EXPENSE, curRange[0], curRange[1]);
        if (curExpenses == null) curExpenses = BigDecimal.ZERO;
        BigDecimal curNetSavings = curIncome.subtract(curExpenses);

        BigDecimal prevIncome = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                userId, TransactionType.INCOME, prevRange[0], prevRange[1]);
        if (prevIncome == null) prevIncome = BigDecimal.ZERO;
        BigDecimal prevExpenses = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                userId, TransactionType.EXPENSE, prevRange[0], prevRange[1]);
        if (prevExpenses == null) prevExpenses = BigDecimal.ZERO;
        BigDecimal prevNetSavings = prevIncome.subtract(prevExpenses);

        TrendMetric incomeTrend = buildTrendMetric(curIncome, prevIncome);
        TrendMetric expenseTrend = buildTrendMetric(curExpenses, prevExpenses);
        TrendMetric netSavingsTrend = buildTrendMetric(curNetSavings, prevNetSavings);

        BigDecimal curSavingsRate = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (curIncome.compareTo(BigDecimal.ZERO) > 0) {
            curSavingsRate = curNetSavings.max(BigDecimal.ZERO)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(curIncome, 2, RoundingMode.HALF_UP);
        }

        BigDecimal prevSavingsRate = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (prevIncome.compareTo(BigDecimal.ZERO) > 0) {
            prevSavingsRate = prevNetSavings.max(BigDecimal.ZERO)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(prevIncome, 2, RoundingMode.HALF_UP);
        }

        BigDecimal savingsRateChange = curSavingsRate.subtract(prevSavingsRate);

        return new PeriodComparisonResponse(
                curRange[0],
                curRange[1],
                prevRange[0],
                prevRange[1],
                incomeTrend,
                expenseTrend,
                netSavingsTrend,
                curSavingsRate,
                prevSavingsRate,
                savingsRateChange
        );
    }

    @Transactional(readOnly = true)
    public CategoryBreakdownResponse getCategoryBreakdown(LocalDate fromDate, LocalDate toDate) {
        UUID userId = currentUser.require().getUserId();
        LocalDate[] curRange = normalizeRange(fromDate, toDate);
        LocalDate[] prevRange = resolvePreviousPeriod(curRange[0], curRange[1]);

        BigDecimal totalExpenses = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                userId, TransactionType.EXPENSE, curRange[0], curRange[1]);

        List<CategorySpend> curSpends = transactionRepository.findCategorySpending(
                userId, TransactionType.EXPENSE, curRange[0], curRange[1]);
        List<CategorySpend> prevSpends = transactionRepository.findCategorySpending(
                userId, TransactionType.EXPENSE, prevRange[0], prevRange[1]);

        Map<TransactionCategory, BigDecimal> prevMap = new EnumMap<>(TransactionCategory.class);
        for (CategorySpend cs : prevSpends) {
            prevMap.put(cs.category(), cs.totalAmount());
        }

        List<Object[]> reviewRows = transactionRepository.findRequiresReviewCategorySpending(
                userId, TransactionType.EXPENSE, curRange[0], curRange[1]);
        Map<TransactionCategory, Long> reviewCountMap = new EnumMap<>(TransactionCategory.class);
        Map<TransactionCategory, BigDecimal> reviewAmtMap = new EnumMap<>(TransactionCategory.class);
        if (reviewRows != null) {
            for (Object[] row : reviewRows) {
                if (row != null && row.length >= 3 && row[0] instanceof TransactionCategory cat) {
                    Long cnt = row[1] instanceof Long c ? c : (row[1] instanceof Number n ? n.longValue() : 0L);
                    BigDecimal amt = row[2] instanceof BigDecimal b ? b : BigDecimal.ZERO;
                    reviewCountMap.put(cat, cnt != null ? cnt : 0L);
                    reviewAmtMap.put(cat, amt != null ? amt : BigDecimal.ZERO);
                }
            }
        }

        long totalTx = transactionRepository.countByUserIdAndTypeAndDateBetween(
                userId, TransactionType.EXPENSE, curRange[0], curRange[1]);
        long reviewTx = transactionRepository.countRequiresReviewByUserIdAndTypeAndDateBetween(
                userId, TransactionType.EXPENSE, curRange[0], curRange[1]);
        long autoTx = Math.max(0, totalTx - reviewTx);

        BigDecimal autoPct = BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        BigDecimal reviewPct = BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        if (totalTx > 0) {
            autoPct = BigDecimal.valueOf(autoTx).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalTx), 1, RoundingMode.HALF_UP);
            reviewPct = BigDecimal.valueOf(reviewTx).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalTx), 1, RoundingMode.HALF_UP);
        }

        Map<TransactionCategory, CategorySpend> curMap = new EnumMap<>(TransactionCategory.class);
        for (CategorySpend cs : curSpends) {
            curMap.put(cs.category(), cs);
        }

        List<CategoryAnalysis> analyses = new ArrayList<>();
        TransactionCategory topCategory = null;
        BigDecimal maxSpend = BigDecimal.ZERO;

        for (CategorySpend cs : curSpends) {
            BigDecimal curAmt = cs.totalAmount();
            BigDecimal prevAmt = prevMap.getOrDefault(cs.category(), BigDecimal.ZERO);
            BigDecimal changeAmt = curAmt.subtract(prevAmt);

            BigDecimal pctOfTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (totalExpenses.compareTo(BigDecimal.ZERO) > 0) {
                pctOfTotal = curAmt.multiply(BigDecimal.valueOf(100))
                        .divide(totalExpenses, 2, RoundingMode.HALF_UP);
            }

            BigDecimal pctChange = calculatePercentageChange(curAmt, prevAmt);
            TrendDirection direction = determineDirection(curAmt, prevAmt);

            if (curAmt.compareTo(maxSpend) > 0) {
                maxSpend = curAmt;
                topCategory = cs.category();
            }

            long catReviewCount = reviewCountMap.getOrDefault(cs.category(), 0L);
            BigDecimal catReviewAmt = reviewAmtMap.getOrDefault(cs.category(), BigDecimal.ZERO);

            analyses.add(new CategoryAnalysis(
                    cs.category(),
                    curAmt,
                    pctOfTotal,
                    prevAmt,
                    changeAmt,
                    pctChange,
                    direction,
                    cs.count(),
                    catReviewCount,
                    catReviewAmt
            ));
        }

        for (Map.Entry<TransactionCategory, BigDecimal> entry : prevMap.entrySet()) {
            if (!curMap.containsKey(entry.getKey()) && entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal prevAmt = entry.getValue();
                BigDecimal changeAmt = prevAmt.negate();
                BigDecimal pctChange = BigDecimal.valueOf(-100.00).setScale(2, RoundingMode.HALF_UP);

                analyses.add(new CategoryAnalysis(
                        entry.getKey(),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        prevAmt,
                        changeAmt,
                        pctChange,
                        TrendDirection.DECREASED,
                        0L,
                        0L,
                        BigDecimal.ZERO
                ));
            }
        }

        return new CategoryBreakdownResponse(
                totalExpenses,
                analyses,
                topCategory,
                totalTx,
                autoTx,
                reviewTx,
                autoPct,
                reviewPct
        );
    }

    @Transactional(readOnly = true)
    public SpendingTrendResponse getTrends(AggregationPeriod period, LocalDate fromDate, LocalDate toDate) {
        UUID userId = currentUser.require().getUserId();
        LocalDate[] range = normalizeRange(fromDate, toDate);
        AggregationPeriod agg = period != null ? period : AggregationPeriod.DAILY;

        List<SpendingTrendPoint> points;
        if (agg == AggregationPeriod.DAILY) {
            points = buildDailyTrend(userId, range[0], range[1]);
        } else if (agg == AggregationPeriod.WEEKLY) {
            points = buildWeeklyTrend(userId, range[0], range[1]);
        } else {
            points = buildMonthlyTrend(userId, range[0], range[1]);
        }

        return new SpendingTrendResponse(agg, points, range[0], range[1]);
    }

    private List<SpendingTrendPoint> buildDailyTrend(UUID userId, LocalDate fromDate, LocalDate toDate) {
        List<DailySpend> rawDaily = transactionRepository.findDailySpending(userId, fromDate, toDate);

        Map<LocalDate, DailyBucket> map = new HashMap<>();
        for (DailySpend ds : rawDaily) {
            DailyBucket bucket = map.computeIfAbsent(ds.date(), DailyBucket::new);
            if (ds.type() == TransactionType.EXPENSE) {
                bucket.expense = bucket.expense.add(ds.totalAmount());
                bucket.count += ds.count();
            } else if (ds.type() == TransactionType.INCOME) {
                bucket.income = bucket.income.add(ds.totalAmount());
                bucket.count += ds.count();
            }
        }

        List<SpendingTrendPoint> points = new ArrayList<>();
        LocalDate cur = fromDate;
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("MMM dd");

        while (!cur.isAfter(toDate)) {
            DailyBucket bucket = map.get(cur);
            BigDecimal expense = bucket != null ? bucket.expense : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal income = bucket != null ? bucket.income : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            long count = bucket != null ? bucket.count : 0L;

            points.add(new SpendingTrendPoint(
                    cur.format(labelFmt),
                    cur,
                    expense,
                    income,
                    count
            ));
            cur = cur.plusDays(1);
        }

        return points;
    }

    private List<SpendingTrendPoint> buildWeeklyTrend(UUID userId, LocalDate fromDate, LocalDate toDate) {
        List<SpendingTrendPoint> daily = buildDailyTrend(userId, fromDate, toDate);
        List<SpendingTrendPoint> weekly = new ArrayList<>();

        int weekNumber = 1;
        BigDecimal weekExpense = BigDecimal.ZERO;
        BigDecimal weekIncome = BigDecimal.ZERO;
        long weekCount = 0;
        LocalDate weekStartDate = fromDate;

        for (int i = 0; i < daily.size(); i++) {
            SpendingTrendPoint pt = daily.get(i);
            weekExpense = weekExpense.add(pt.totalExpense());
            weekIncome = weekIncome.add(pt.totalIncome());
            weekCount += pt.transactionCount();

            if ((i + 1) % 7 == 0 || i == daily.size() - 1) {
                weekly.add(new SpendingTrendPoint(
                        "Week " + weekNumber,
                        weekStartDate,
                        weekExpense,
                        weekIncome,
                        weekCount
                ));
                weekNumber++;
                weekExpense = BigDecimal.ZERO;
                weekIncome = BigDecimal.ZERO;
                weekCount = 0;
                if (i + 1 < daily.size()) {
                    weekStartDate = daily.get(i + 1).date();
                }
            }
        }

        return weekly;
    }

    private List<SpendingTrendPoint> buildMonthlyTrend(UUID userId, LocalDate fromDate, LocalDate toDate) {
        YearMonth startYm = YearMonth.from(fromDate);
        YearMonth endYm = YearMonth.from(toDate);
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("MMM yyyy");

        List<SpendingTrendPoint> points = new ArrayList<>();
        YearMonth curYm = startYm;

        while (!curYm.isAfter(endYm)) {
            LocalDate mStart = curYm.atDay(1);
            LocalDate mEnd = curYm.atEndOfMonth();

            LocalDate actualFrom = mStart.isBefore(fromDate) ? fromDate : mStart;
            LocalDate actualTo = mEnd.isAfter(toDate) ? toDate : mEnd;

            BigDecimal expense = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                    userId, TransactionType.EXPENSE, actualFrom, actualTo);
            BigDecimal income = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                    userId, TransactionType.INCOME, actualFrom, actualTo);
            long count = transactionRepository.countByUserIdAndTypeAndDateBetween(
                    userId, TransactionType.EXPENSE, actualFrom, actualTo)
                    + transactionRepository.countByUserIdAndTypeAndDateBetween(
                    userId, TransactionType.INCOME, actualFrom, actualTo);

            points.add(new SpendingTrendPoint(
                    curYm.format(monthFmt),
                    actualFrom,
                    expense,
                    income,
                    count
            ));

            curYm = curYm.plusMonths(1);
        }

        return points;
    }

    public LocalDate[] normalizeRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null) {
            if (toDate.isBefore(fromDate)) {
                return new LocalDate[]{toDate, fromDate};
            }
            return new LocalDate[]{fromDate, toDate};
        }
        YearMonth cur = YearMonth.now();
        return new LocalDate[]{cur.atDay(1), cur.atEndOfMonth()};
    }

    public LocalDate[] resolvePreviousPeriod(LocalDate fromDate, LocalDate toDate) {
        if (fromDate.getDayOfMonth() == 1 && toDate.equals(YearMonth.from(fromDate).atEndOfMonth())) {
            YearMonth prevYm = YearMonth.from(fromDate).minusMonths(1);
            return new LocalDate[]{prevYm.atDay(1), prevYm.atEndOfMonth()};
        }
        long days = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        LocalDate prevTo = fromDate.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1);
        return new LocalDate[]{prevFrom, prevTo};
    }

    private TrendMetric buildTrendMetric(BigDecimal current, BigDecimal previous) {
        BigDecimal change = current.subtract(previous);
        BigDecimal pctChange = calculatePercentageChange(current, previous);
        TrendDirection direction = determineDirection(current, previous);
        return new TrendMetric(current, previous, change, pctChange, direction);
    }

    private BigDecimal calculatePercentageChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous.abs(), 2, RoundingMode.HALF_UP);
    }

    private TrendDirection determineDirection(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) <= 0) {
            return TrendDirection.UNCHANGED;
        }
        int cmp = current.compareTo(previous);
        if (cmp > 0) return TrendDirection.INCREASED;
        if (cmp < 0) return TrendDirection.DECREASED;
        return TrendDirection.UNCHANGED;
    }

    private static class DailyBucket {
        final LocalDate date;
        BigDecimal expense = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal income = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        long count = 0;

        DailyBucket(LocalDate date) {
            this.date = date;
        }
    }
}
