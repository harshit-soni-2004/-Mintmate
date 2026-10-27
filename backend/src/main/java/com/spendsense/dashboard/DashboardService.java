package com.spendsense.dashboard;

import com.spendsense.dashboard.dto.CategorySpend;
import com.spendsense.dashboard.dto.CategorySpendingResponse;
import com.spendsense.dashboard.dto.DailySpend;
import com.spendsense.dashboard.dto.DailySpendingResponse;
import com.spendsense.dashboard.dto.DashboardDataResponse;
import com.spendsense.dashboard.dto.DashboardSummaryResponse;
import com.spendsense.security.CurrentUser;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionType;
import com.spendsense.transaction.dto.TransactionResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final TransactionRepository transactionRepository;
    private final CurrentUser currentUser;
    private final com.spendsense.onboarding.UserFinancialProfileRepository profileRepository;
    private final com.spendsense.onboarding.UserIncomeSourceRepository incomeSourceRepository;
    private final com.spendsense.debt.DebtRepository debtRepository;

    public DashboardService(TransactionRepository transactionRepository, CurrentUser currentUser) {
        this(transactionRepository, currentUser, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DashboardService(
            TransactionRepository transactionRepository,
            CurrentUser currentUser,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.spendsense.onboarding.UserFinancialProfileRepository profileRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.spendsense.onboarding.UserIncomeSourceRepository incomeSourceRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.spendsense.debt.DebtRepository debtRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.currentUser = currentUser;
        this.profileRepository = profileRepository;
        this.incomeSourceRepository = incomeSourceRepository;
        this.debtRepository = debtRepository;
    }

    @Transactional(readOnly = true)
    public DashboardDataResponse getDashboardData(String month, LocalDate fromDateParam, LocalDate toDateParam) {
        UUID userId = currentUser.require().getUserId();

        LocalDate fromDate;
        LocalDate toDate;
        LocalDate prevFromDate;
        LocalDate prevToDate;

        if (fromDateParam != null && toDateParam != null) {
            fromDate = fromDateParam;
            toDate = toDateParam;
            long days = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
            prevToDate = fromDate.minusDays(1);
            prevFromDate = prevToDate.minusDays(days - 1);
        } else if (month != null && !month.isBlank()) {
            YearMonth yearMonth = YearMonth.parse(month.trim());
            fromDate = yearMonth.atDay(1);
            toDate = yearMonth.atEndOfMonth();
            YearMonth prevYearMonth = yearMonth.minusMonths(1);
            prevFromDate = prevYearMonth.atDay(1);
            prevToDate = prevYearMonth.atEndOfMonth();
        } else {
            YearMonth currentMonth = YearMonth.now();
            fromDate = currentMonth.atDay(1);
            toDate = currentMonth.atEndOfMonth();
            YearMonth prevMonth = currentMonth.minusMonths(1);
            prevFromDate = prevMonth.atDay(1);
            prevToDate = prevMonth.atEndOfMonth();
        }

        BigDecimal totalIncome = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                userId, TransactionType.INCOME, fromDate, toDate);
        BigDecimal totalExpenses = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                userId, TransactionType.EXPENSE, fromDate, toDate);
        BigDecimal netSavings = totalIncome.subtract(totalExpenses);

        BigDecimal savingsRate = BigDecimal.ZERO;
        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            savingsRate = netSavings.max(BigDecimal.ZERO)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(totalIncome, 1, RoundingMode.HALF_UP);
        }

        BigDecimal prevIncome = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                userId, TransactionType.INCOME, prevFromDate, prevToDate);
        BigDecimal prevExpenses = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                userId, TransactionType.EXPENSE, prevFromDate, prevToDate);

        BigDecimal incomeChangePct = calculateChange(totalIncome, prevIncome);
        BigDecimal expenseChangePct = calculateChange(totalExpenses, prevExpenses);

        BigDecimal totalTransfers = transactionRepository.sumAmountByUserIdAndTypeAndDirectionAndDateBetween(
                userId, TransactionType.TRANSFER, com.spendsense.transaction.TransferDirection.OUT, fromDate, toDate);
        if (totalTransfers == null) totalTransfers = BigDecimal.ZERO;

        BigDecimal investmentContributions = transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(
                userId, TransactionType.INVESTMENT, fromDate, toDate);
        if (investmentContributions == null) investmentContributions = BigDecimal.ZERO;

        BigDecimal declaredIncome = null;
        BigDecimal savingsTarget = null;
        BigDecimal investTarget = null;
        boolean hasOnboardingBaseline = false;

        if (profileRepository != null) {
            var profileOpt = profileRepository.findByUser_Id(userId);
            if (profileOpt.isPresent()) {
                var prof = profileOpt.get();
                savingsTarget = prof.getMonthlySavingsTarget();
                investTarget = prof.getMonthlyInvestmentTarget();
                if (incomeSourceRepository != null) {
                    var sources = incomeSourceRepository.findByUser_Id(userId);
                    if (!sources.isEmpty()) {
                        declaredIncome = sources.stream()
                                .map(s -> s.getDeclaredAmount() != null ? s.getDeclaredAmount() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                    }
                }
                if (totalIncome.compareTo(BigDecimal.ZERO) == 0 && declaredIncome != null && declaredIncome.compareTo(BigDecimal.ZERO) > 0) {
                    hasOnboardingBaseline = true;
                }
            }
        }

        BigDecimal debtOwedToUser = BigDecimal.ZERO;
        BigDecimal debtOwedByUser = BigDecimal.ZERO;
        int activeDebtRemindersCount = 0;
        if (debtRepository != null) {
            debtOwedToUser = debtRepository.sumActiveAmountByUserIdAndDebtType(userId, com.spendsense.debt.DebtType.OWED_TO_ME);
            debtOwedByUser = debtRepository.sumActiveAmountByUserIdAndDebtType(userId, com.spendsense.debt.DebtType.I_OWE);
            activeDebtRemindersCount = debtRepository.findOverdueDebts(userId, LocalDate.now()).size()
                    + debtRepository.findUpcomingDebts(userId, LocalDate.now()).size();
        }
        BigDecimal netDebtPosition = debtOwedToUser.subtract(debtOwedByUser);

        DashboardSummaryResponse summary = new DashboardSummaryResponse(
                totalIncome,
                totalExpenses,
                netSavings,
                savingsRate,
                prevIncome,
                prevExpenses,
                incomeChangePct,
                expenseChangePct,
                totalTransfers,
                investmentContributions,
                declaredIncome,
                savingsTarget,
                investTarget,
                hasOnboardingBaseline,
                debtOwedToUser,
                debtOwedByUser,
                netDebtPosition,
                activeDebtRemindersCount
        );

        List<CategorySpend> categorySpends = transactionRepository.findCategorySpending(
                userId, TransactionType.EXPENSE, fromDate, toDate);
        List<CategorySpendingResponse> categorySpending = new ArrayList<>();
        for (CategorySpend cs : categorySpends) {
            BigDecimal pct = BigDecimal.ZERO;
            if (totalExpenses.compareTo(BigDecimal.ZERO) > 0) {
                pct = cs.totalAmount().multiply(BigDecimal.valueOf(100))
                        .divide(totalExpenses, 1, RoundingMode.HALF_UP);
            }
            categorySpending.add(new CategorySpendingResponse(
                    cs.category(), cs.totalAmount(), pct, cs.count()));
        }

        List<DailySpend> dailySpends = transactionRepository.findDailySpending(userId, fromDate, toDate);
        Map<LocalDate, DailyAccumulator> dailyMap = new LinkedHashMap<>();
        for (DailySpend ds : dailySpends) {
            DailyAccumulator acc = dailyMap.computeIfAbsent(ds.date(), k -> new DailyAccumulator(ds.date()));
            if (ds.type() == TransactionType.EXPENSE) {
                acc.totalExpense = acc.totalExpense.add(ds.totalAmount());
                acc.transactionCount += ds.count();
            } else if (ds.type() == TransactionType.INCOME) {
                acc.totalIncome = acc.totalIncome.add(ds.totalAmount());
                acc.transactionCount += ds.count();
            }
        }

        List<DailySpendingResponse> dailyResponses = dailyMap.values().stream()
                .map(acc -> new DailySpendingResponse(acc.date, acc.totalExpense, acc.totalIncome, acc.transactionCount))
                .toList();

        List<TransactionResponse> recentTransactions = transactionRepository
                .findTop10ByUser_IdOrderByTransactionDateDescCreatedAtDesc(userId).stream()
                .map(TransactionResponse::from)
                .toList();

        List<TransactionResponse> periodTransactions = transactionRepository
                .findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(userId, fromDate, toDate).stream()
                .map(TransactionResponse::from)
                .toList();

        return new DashboardDataResponse(
                summary,
                categorySpending,
                dailyResponses,
                recentTransactions,
                periodTransactions,
                fromDate,
                toDate
        );
    }

    private static BigDecimal calculateChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP);
    }

    private static class DailyAccumulator {
        private final LocalDate date;
        private BigDecimal totalExpense = BigDecimal.ZERO;
        private BigDecimal totalIncome = BigDecimal.ZERO;
        private long transactionCount = 0;

        public DailyAccumulator(LocalDate date) {
            this.date = date;
        }
    }
}
