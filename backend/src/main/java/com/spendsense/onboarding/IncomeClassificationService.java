package com.spendsense.onboarding;

import com.spendsense.ingestion.SourceTransactionClassification;
import com.spendsense.transaction.IncomeTier;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionType;
import com.spendsense.user.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IncomeClassificationService {

    private static final Logger log = LoggerFactory.getLogger(IncomeClassificationService.class);

    private static final Pattern SALARY_PATTERN = Pattern.compile(
            "\\b(SALARY|SAL[\\s/_-]?CR|ACH[\\s/_-]?CR|CMS[\\s/_-]|PAYROLL|WAGES|STIPEND|HONORARIUM)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REFUND_PATTERN = Pattern.compile(
            "\\b(REFUND|REVERSAL|REVERSE|CHARGEBACK|CASHBACK|RET|RETURN)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern P2P_AD_HOC_PATTERN = Pattern.compile(
            "\\b(UPI[\\s/_-]?CR|IMPS[\\s/_-]?P2P|P2P|GIFT|BORROW|REPAY|REIMBURSE)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final BigDecimal DIVERGENCE_THRESHOLD_PERCENT = BigDecimal.valueOf(10.0);

    private final UserFinancialProfileRepository profileRepository;
    private final UserIncomeSourceRepository incomeSourceRepository;
    private final TransactionRepository transactionRepository;

    public IncomeClassificationService(UserFinancialProfileRepository profileRepository,
                                     UserIncomeSourceRepository incomeSourceRepository,
                                     TransactionRepository transactionRepository) {
        this.profileRepository = profileRepository;
        this.incomeSourceRepository = incomeSourceRepository;
        this.transactionRepository = transactionRepository;
    }

    public IncomeClassificationResult classifyCredit(User user,
                                                    BigDecimal amount,
                                                    LocalDate transactionDate,
                                                    String rawDescription,
                                                    String merchant,
                                                    SourceTransactionClassification sourceClassification) {
        String desc = (rawDescription != null ? rawDescription : "") + " " + (merchant != null ? merchant : "");
        String descUpper = desc.toUpperCase(Locale.ROOT);

        // 1. Tier 3: Refund / Reimbursement offset
        if (sourceClassification == SourceTransactionClassification.REFUND || REFUND_PATTERN.matcher(descUpper).find()) {
            return IncomeClassificationResult.tier3Refund();
        }

        // Retrieve user's declared profile and income sources if available
        Optional<UserFinancialProfile> profileOpt = user != null ? profileRepository.findByUser_Id(user.getId()) : Optional.empty();
        List<UserIncomeSource> incomeSources = user != null ? incomeSourceRepository.findByUser_Id(user.getId()) : List.of();

        boolean hasSalaryKeyword = SALARY_PATTERN.matcher(descUpper).find();

        // 2. Check against declared income sources
        if (!incomeSources.isEmpty()) {
            for (UserIncomeSource source : incomeSources) {
                boolean keywordMatches = (source.getEmployerOrPayerKeyword() != null
                        && !source.getEmployerOrPayerKeyword().isBlank()
                        && descUpper.contains(source.getEmployerOrPayerKeyword().toUpperCase(Locale.ROOT)));

                if (source.getIncomeType() == IncomeType.SALARY || source.getIncomeType() == IncomeType.STIPEND) {
                    if (hasSalaryKeyword || keywordMatches) {
                        return evaluateSalaryMatch(source, amount, transactionDate);
                    }
                } else if (source.getIncomeType() == IncomeType.FREELANCE || source.getIncomeType() == IncomeType.BUSINESS_REVENUE) {
                    if (source.isVariable() || keywordMatches) {
                        return IncomeClassificationResult.tier2Variable(source.getId());
                    }
                }
            }
        }

        // 3. Fallback when user profile exists but no direct source keyword match
        if (profileOpt.isPresent()) {
            UserPersona persona = profileOpt.get().getPersona();
            if (persona == UserPersona.FREELANCER || persona == UserPersona.BUSINESS_OWNER) {
                // If freelancer/business and not explicitly P2P repayment
                if (!P2P_AD_HOC_PATTERN.matcher(descUpper).find()) {
                    return IncomeClassificationResult.tier2Variable(null);
                }
            } else if (persona == UserPersona.SALARIED) {
                if (hasSalaryKeyword) {
                    return IncomeClassificationResult.tier1Salary(null, false, false, "Salary keyword detected", null);
                }
            }
        }

        // 4. If transaction explicitly mentions salary regardless of declared profile
        if (hasSalaryKeyword) {
            return IncomeClassificationResult.tier1Salary(null, false, false, "Salary narration matched", null);
        }

        // 5. Default for all other incoming credits (P2P transfers, repayments, gifts, one-off deposits) -> Tier 4
        return IncomeClassificationResult.tier4OtherCredit("Ad-hoc or discretionary credit, excluded from income metric");
    }

    private IncomeClassificationResult evaluateSalaryMatch(UserIncomeSource source, BigDecimal actualAmount, LocalDate txDate) {
        BigDecimal declared = source.getDeclaredAmount();
        BigDecimal divergencePct = BigDecimal.ZERO;
        boolean requiresReview = false;
        String reason = null;

        if (declared != null && declared.compareTo(BigDecimal.ZERO) > 0 && actualAmount != null) {
            BigDecimal diff = actualAmount.subtract(declared).abs();
            divergencePct = diff.multiply(BigDecimal.valueOf(100)).divide(declared, 2, RoundingMode.HALF_UP);
            if (divergencePct.compareTo(DIVERGENCE_THRESHOLD_PERCENT) > 0) {
                requiresReview = true;
                reason = String.format("Salary credit of ₹%.2f diverges from declared ₹%.2f by %.1f%%",
                        actualAmount, declared, divergencePct);
            }
        }

        boolean dateMatches = isDateWithinExpectedWindow(source.getExpectedDayOfMonth(), txDate);
        boolean isVerified = !requiresReview && dateMatches;

        return IncomeClassificationResult.tier1Salary(source.getId(), isVerified, requiresReview, reason, divergencePct);
    }

    public boolean isDateWithinExpectedWindow(Integer expectedDayOfMonth, LocalDate txDate) {
        if (expectedDayOfMonth == null || txDate == null) {
            return true; // No constraint declared
        }
        int actualDay = txDate.getDayOfMonth();
        int daysDiff = Math.abs(actualDay - expectedDayOfMonth);

        // Account for end-of-month / beginning-of-month boundary (e.g. expected 1st, credited 30th/31st)
        if (expectedDayOfMonth <= 5 && actualDay >= 26) {
            int daysInPrevMonth = YearMonth.from(txDate).lengthOfMonth();
            daysDiff = (daysInPrevMonth - actualDay) + expectedDayOfMonth;
        } else if (expectedDayOfMonth >= 26 && actualDay <= 5) {
            int daysInPrevMonth = YearMonth.from(txDate.minusMonths(1)).lengthOfMonth();
            daysDiff = (daysInPrevMonth - expectedDayOfMonth) + actualDay;
        }

        return daysDiff <= 4;
    }

    public Optional<String> checkMissingSalaryCredit(UUID userId, LocalDate currentDate) {
        List<UserIncomeSource> sources = incomeSourceRepository.findByUser_Id(userId);
        if (sources.isEmpty()) return Optional.empty();

        for (UserIncomeSource source : sources) {
            if (source.getIncomeType() == IncomeType.SALARY && source.getExpectedDayOfMonth() != null) {
                int expectedDay = source.getExpectedDayOfMonth();
                int currentDay = currentDate.getDayOfMonth();

                // If currently 5 or more days past expected day in the month
                if (currentDay > expectedDay + 4) {
                    LocalDate startOfMonth = currentDate.withDayOfMonth(1);
                    LocalDate endOfMonth = YearMonth.from(currentDate).atEndOfMonth();

                    List<Transaction> salaryTx = transactionRepository
                            .findByUser_IdAndTransactionTypeAndTransactionDateBetweenOrderByAmountDesc(
                                    userId, TransactionType.INCOME, startOfMonth, endOfMonth);

                    boolean foundSalary = salaryTx.stream()
                            .anyMatch(t -> t.getIncomeTier() == IncomeTier.TIER_1_SALARY || SALARY_PATTERN.matcher(t.getMerchant() + " " + t.getDescription()).find());

                    if (!foundSalary) {
                        return Optional.of(String.format("Expected salary credit of ₹%.2f around day %d has not been detected for %s.",
                                source.getDeclaredAmount(), expectedDay, currentDate.getMonth().name()));
                    }
                }
            }
        }
        return Optional.empty();
    }
}
