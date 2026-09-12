package com.spendsense.budget;

import com.spendsense.budget.dto.BudgetRequest;
import com.spendsense.budget.dto.BudgetResponse;
import com.spendsense.budget.dto.BudgetSummaryResponse;
import com.spendsense.common.exception.ApiException;
import com.spendsense.security.CurrentUser;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionType;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {

    public static final double WARNING_THRESHOLD_PERCENT = 75.0;
    public static final double EXCEEDED_THRESHOLD_PERCENT = 100.0;

    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public BudgetService(BudgetRepository budgetRepository,
                         TransactionRepository transactionRepository,
                         UserRepository userRepository,
                         CurrentUser currentUser) {
        this.budgetRepository = budgetRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
    }

    @Transactional
    public BudgetResponse create(BudgetRequest request) {
        validateRequest(request, null);
        User user = requireUser();

        BudgetPeriod period = request.period() != null ? request.period() : BudgetPeriod.MONTHLY;
        Budget budget = new Budget(
                user,
                request.category(),
                request.limitAmount(),
                period,
                request.startDate(),
                request.endDate()
        );

        Budget saved = budgetRepository.save(budget);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public BudgetResponse get(UUID id) {
        Budget budget = findOwned(id);
        return toResponse(budget);
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> list(String yearMonth, LocalDate fromDateParam, LocalDate toDateParam) {
        UUID userId = currentUser.require().getUserId();
        LocalDate[] range = resolveDateRange(yearMonth, fromDateParam, toDateParam);

        List<Budget> budgets;
        if (range != null) {
            budgets = budgetRepository.findActiveBudgets(userId, range[0], range[1]);
        } else {
            budgets = budgetRepository.findByUser_IdOrderByCategoryAsc(userId);
        }

        return budgets.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BudgetSummaryResponse getSummary(String yearMonth, LocalDate fromDateParam, LocalDate toDateParam) {
        List<BudgetResponse> budgets = list(yearMonth, fromDateParam, toDateParam);

        BigDecimal totalBudget = budgets.stream()
                .map(BudgetResponse::limitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSpent = budgets.stream()
                .map(BudgetResponse::spentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRemaining = totalBudget.subtract(totalSpent);

        double percentageUsed = totalBudget.compareTo(BigDecimal.ZERO) > 0
                ? totalSpent.divide(totalBudget, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue()
                : 0.0;

        int onTrackCount = (int) budgets.stream().filter(b -> b.status() == BudgetStatus.ON_TRACK).count();
        int warningCount = (int) budgets.stream().filter(b -> b.status() == BudgetStatus.WARNING).count();
        int exceededCount = (int) budgets.stream().filter(b -> b.status() == BudgetStatus.EXCEEDED).count();
        int totalBudgets = budgets.size();

        return new BudgetSummaryResponse(
                totalBudget,
                totalSpent,
                totalRemaining,
                percentageUsed,
                onTrackCount,
                warningCount,
                exceededCount,
                totalBudgets,
                budgets
        );
    }

    @Transactional
    public BudgetResponse update(UUID id, BudgetRequest request) {
        validateRequest(request, id);
        Budget budget = findOwned(id);

        budget.update(
                request.category(),
                request.limitAmount(),
                request.period(),
                request.startDate(),
                request.endDate()
        );

        Budget saved = budgetRepository.save(budget);
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Budget budget = findOwned(id);
        budgetRepository.delete(budget);
    }

    public BudgetResponse toResponse(Budget budget) {
        UUID userId = budget.getUser().getId();
        BigDecimal spent = transactionRepository.sumAmountByUserIdAndCategoryAndTypeAndDateBetween(
                userId,
                budget.getCategory(),
                TransactionType.EXPENSE,
                budget.getStartDate(),
                budget.getEndDate()
        );

        long count = transactionRepository.countByUserIdAndCategoryAndTypeAndDateBetween(
                userId,
                budget.getCategory(),
                TransactionType.EXPENSE,
                budget.getStartDate(),
                budget.getEndDate()
        );

        BigDecimal limit = budget.getLimitAmount();
        BigDecimal remaining = limit.subtract(spent);

        double percentage = limit.compareTo(BigDecimal.ZERO) > 0
                ? spent.divide(limit, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue()
                : 0.0;

        BudgetStatus status;
        if (percentage >= EXCEEDED_THRESHOLD_PERCENT) {
            status = BudgetStatus.EXCEEDED;
        } else if (percentage >= WARNING_THRESHOLD_PERCENT) {
            status = BudgetStatus.WARNING;
        } else {
            status = BudgetStatus.ON_TRACK;
        }

        return BudgetResponse.of(budget, spent, remaining, percentage, count, status);
    }

    private Budget findOwned(UUID id) {
        UUID userId = currentUser.require().getUserId();
        return budgetRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BUDGET_NOT_FOUND", "Budget not found"));
    }

    private User requireUser() {
        UUID userId = currentUser.require().getUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required"));
    }

    private void validateRequest(BudgetRequest request, UUID excludeId) {
        if (request.limitAmount() == null || request.limitAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LIMIT_AMOUNT", "Limit amount must be greater than zero");
        }
        if (request.startDate() == null || request.endDate() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATES", "Start date and end date are required");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "End date cannot be before start date");
        }

        UUID userId = currentUser.require().getUserId();
        BudgetPeriod period = request.period() != null ? request.period() : BudgetPeriod.MONTHLY;

        boolean exists = excludeId == null
                ? budgetRepository.existsByUser_IdAndCategoryAndPeriodAndStartDate(
                userId, request.category(), period, request.startDate())
                : budgetRepository.existsByUser_IdAndCategoryAndPeriodAndStartDateAndIdNot(
                userId, request.category(), period, request.startDate(), excludeId);

        if (exists) {
            throw new ApiException(HttpStatus.CONFLICT, "BUDGET_ALREADY_EXISTS",
                    "A budget already exists for category " + request.category() + " starting on " + request.startDate());
        }
    }

    private LocalDate[] resolveDateRange(String yearMonth, LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null) {
            return new LocalDate[]{fromDate, toDate};
        }
        if (yearMonth != null && !yearMonth.isBlank()) {
            try {
                YearMonth ym = YearMonth.parse(yearMonth.trim());
                return new LocalDate[]{ym.atDay(1), ym.atEndOfMonth()};
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
